package io.github.coolcoderme.haphone.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import io.github.coolcoderme.haphone.control.Controllers
import io.github.coolcoderme.haphone.control.KasaCodec
import io.github.coolcoderme.haphone.control.LifxCodec
import io.github.coolcoderme.haphone.data.HubRepository
import io.github.coolcoderme.haphone.data.model.DeviceKind
import io.github.coolcoderme.haphone.data.model.DiscoveredDevice
import io.github.coolcoderme.haphone.net.Http
import io.github.coolcoderme.haphone.util.Text
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.nio.charset.Charset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class DiscoveryCoordinator(
    private val context: Context,
    private val repo: HubRepository,
    private val scope: CoroutineScope
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private var scanJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        acquireMulticast()
        startMdns()
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { ssdpSearch() }
                runCatching { kasaSearch() }
                runCatching { lifxSearch() }
                delay(45_000)
            }
        }
    }

    fun stop() {
        scanJob?.cancel()
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        listeners.clear()
        multicastLock?.release()
        multicastLock = null
    }

    fun scanNow() {
        scope.launch(Dispatchers.IO) {
            runCatching { ssdpSearch() }
            runCatching { kasaSearch() }
            runCatching { lifxSearch() }
        }
    }

    private fun acquireMulticast() {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("haphone-discovery").apply {
            setReferenceCounted(true)
            acquire()
        }
    }

    private fun startMdns() {
        listOf(
            "_wled._tcp.",
            "_hue._tcp.",
            "_esphomelib._tcp.",
            "_googlecast._tcp.",
            "_http._tcp.",
            "_hap._tcp."
        ).forEach { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    runCatching {
                        nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                scope.launch(Dispatchers.IO) { handleResolved(type, resolved) }
                            }
                        })
                    }
                }
            }
            listeners += listener
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
        }
    }

    private suspend fun handleResolved(type: String, info: NsdServiceInfo) {
        val host = info.host?.hostAddress ?: return
        if (host.contains(":")) return
        val port = if (info.port > 0) info.port else 80
        val name = info.serviceName ?: host
        val kind = when {
            type.startsWith("_wled") -> DeviceKind.WLED
            type.startsWith("_hue") -> DeviceKind.HUE
            type.startsWith("_esphomelib") -> DeviceKind.ESPHOME
            type.startsWith("_googlecast") -> DeviceKind.CHROMECAST
            else -> Controllers.probeHttp(host, if (port == 5353) 80 else port) ?: return
        }
        val httpPort = when (kind) {
            DeviceKind.HUE -> 80
            DeviceKind.WLED, DeviceKind.SHELLY, DeviceKind.TASMOTA, DeviceKind.ESPHOME -> if (port in 1..65535 && port != 5353) port else 80
            else -> port
        }
        remember(DiscoveredDevice(kind, name, host, httpPort))
    }

    private fun ssdpSearch() {
        val query = (
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: ssdp:all\r\n\r\n"
            ).toByteArray()
        val socket = MulticastSocket(null)
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(0))
        socket.soTimeout = 2500
        socket.timeToLive = 2
        runCatching {
            val nif = primaryInterface()
            if (nif != null) socket.networkInterface = nif
        }
        val group = InetAddress.getByName("239.255.255.250")
        socket.send(DatagramPacket(query, query.size, group, 1900))
        val buf = ByteArray(2048)
        val seen = HashSet<String>()
        val end = System.currentTimeMillis() + 2500
        while (System.currentTimeMillis() < end) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                val text = String(packet.data, 0, packet.length, Charset.forName("UTF-8"))
                val parsed = Text.parseSsdp(text) ?: continue
                if (!seen.add(parsed.location + parsed.st)) continue
                classifySsdp(parsed, packet.address.hostAddress ?: continue)
            } catch (_: Exception) {
                break
            }
        }
        socket.close()
    }

    private fun classifySsdp(msg: Text.SsdpMessage, fallbackHost: String) {
        val st = msg.st.lowercase()
        val server = msg.server.lowercase()
        val host = Text.hostOf(msg.location) ?: fallbackHost
        when {
            st.contains("roku:ecp") || server.contains("roku") ->
                remember(DiscoveredDevice(DeviceKind.ROKU, "Roku", host, 8060, mapOf("location" to msg.location)))
            st.contains("zoneplayer") || server.contains("sonos") ->
                rememberSonos(host, msg.location)
            st.contains("mediarenderer") ->
                rememberDlna(host, msg.location)
            server.contains("ipbridge") || st.contains("basic:1") && server.contains("hue") ->
                remember(DiscoveredDevice(DeviceKind.HUE, "Hue Bridge", host, 80, mapOf("location" to msg.location)))
            else -> {
                val xml = if (msg.location.startsWith("http")) Http.get(msg.location) else null
                if (xml != null) {
                    when {
                        xml.contains("IpBridge") || xml.contains("Philips hue") ->
                            remember(DiscoveredDevice(DeviceKind.HUE, "Hue Bridge", host, 80))
                        xml.contains("urn:schemas-upnp-org:device:ZonePlayer") ->
                            rememberSonos(host, msg.location)
                        xml.contains("MediaRenderer") ->
                            rememberDlna(host, msg.location)
                    }
                }
            }
        }
    }

    private fun rememberSonos(host: String, location: String) {
        val extras = mutableMapOf("location" to location, "avTransport" to "/MediaRenderer/AVTransport/Control", "rendering" to "/MediaRenderer/RenderingControl/Control")
        remember(DiscoveredDevice(DeviceKind.SONOS, "Sonos", host, 1400, extras))
    }

    private fun rememberDlna(host: String, location: String) {
        val extras = mutableMapOf("location" to location, "avTransport" to "/MediaRenderer/AVTransport/Control")
        val port = Text.portOf(location) ?: 80
        remember(DiscoveredDevice(DeviceKind.DLNA, "DLNA player", host, port, extras))
    }

    private fun kasaSearch() {
        val payload = KasaCodec.encrypt(
            JSONObject().put("system", JSONObject().put("get_sysinfo", JSONObject())).toString().toByteArray(),
            includeLength = false
        )
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 1500
            val packet = DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), 9999)
            socket.send(packet)
            val buf = ByteArray(2048)
            val end = System.currentTimeMillis() + 1500
            while (System.currentTimeMillis() < end) {
                try {
                    val incoming = DatagramPacket(buf, buf.size)
                    socket.receive(incoming)
                    val decoded = String(KasaCodec.decrypt(incoming.data.copyOf(incoming.length), skipLength = false))
                    val info = JSONObject(decoded).optJSONObject("system")?.optJSONObject("get_sysinfo") ?: continue
                    val host = incoming.address.hostAddress ?: continue
                    val name = info.optString("alias", "Kasa")
                    remember(DiscoveredDevice(DeviceKind.KASA, name, host, 9999))
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun lifxSearch() {
        val payload = LifxCodec.discoveryPacket()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 1500
            socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), 56700))
            val buf = ByteArray(128)
            val end = System.currentTimeMillis() + 1500
            while (System.currentTimeMillis() < end) {
                try {
                    val incoming = DatagramPacket(buf, buf.size)
                    socket.receive(incoming)
                    val host = incoming.address.hostAddress ?: continue
                    remember(DiscoveredDevice(DeviceKind.LIFX, "LIFX", host, 56700))
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun remember(item: DiscoveredDevice) {
        scope.launch { repo.rememberDiscovery(item) }
        if (item.kind.autoAdd) {
            scope.launch {
                val already = repo.devices().any { it.host == item.host && it.kind == item.kind.name }
                if (!already) repo.addDiscovered(item)
            }
        }
    }

    private fun primaryInterface(): NetworkInterface? {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        while (interfaces.hasMoreElements()) {
            val nif = interfaces.nextElement()
            if (nif.isUp && !nif.isLoopback && nif.supportsMulticast()) return nif
        }
        return null
    }

}
