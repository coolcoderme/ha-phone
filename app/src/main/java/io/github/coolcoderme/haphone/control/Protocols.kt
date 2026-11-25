package io.github.coolcoderme.haphone.control

import android.util.Log
import io.github.coolcoderme.haphone.data.db.DeviceRow
import io.github.coolcoderme.haphone.data.db.EntityRow
import io.github.coolcoderme.haphone.data.model.DeviceKind
import io.github.coolcoderme.haphone.data.model.EntityKind
import io.github.coolcoderme.haphone.data.model.parseExtras
import io.github.coolcoderme.haphone.net.Http
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

data class RefreshResult(
    val entities: List<EntityRow>,
    val extrasPatch: JSONObject = JSONObject()
)

object Controllers {
    private const val TAG = "Controllers"

    suspend fun refresh(device: DeviceRow): RefreshResult? {
        return runCatching {
            when (DeviceKind.fromStorage(device.kind)) {
                DeviceKind.WLED -> refreshWled(device)
                DeviceKind.SHELLY -> refreshShelly(device)
                DeviceKind.TASMOTA -> refreshTasmota(device)
                DeviceKind.HUE -> refreshHue(device)
                DeviceKind.KASA -> refreshKasa(device)
                DeviceKind.LIFX -> refreshLifx(device)
                DeviceKind.ROKU -> refreshRoku(device)
                DeviceKind.SONOS -> refreshSonos(device)
                DeviceKind.ESPHOME -> refreshEsphome(device)
                DeviceKind.DLNA, DeviceKind.CHROMECAST -> refreshMediaPlaceholder(device)
                DeviceKind.GENERIC_HTTP -> refreshGeneric(device)
                DeviceKind.MQTT -> refreshMqtt(device)
                DeviceKind.UNKNOWN -> null
            }
        }.onFailure {
            Log.w(TAG, "refresh ${device.kind} ${device.host}:${device.port}", it)
        }.getOrNull()
    }

    suspend fun execute(device: DeviceRow, entity: EntityRow, action: String, extra: JSONObject = JSONObject()): Boolean {
        return runCatching {
            when (DeviceKind.fromStorage(device.kind)) {
                DeviceKind.WLED -> execWled(device, action, extra)
                DeviceKind.SHELLY -> execShelly(device, entity, action)
                DeviceKind.TASMOTA -> execTasmota(device, entity, action, extra)
                DeviceKind.HUE -> execHue(device, entity, action, extra)
                DeviceKind.KASA -> execKasa(device, action)
                DeviceKind.LIFX -> execLifx(device, action, extra)
                DeviceKind.ROKU -> execRoku(device, action)
                DeviceKind.SONOS -> execSonos(device, action, extra)
                DeviceKind.ESPHOME -> execEsphome(device, entity, action)
                DeviceKind.DLNA -> execDlna(device, action)
                DeviceKind.GENERIC_HTTP -> execGeneric(device, action)
                DeviceKind.MQTT -> execMqtt(device, action)
                DeviceKind.CHROMECAST, DeviceKind.UNKNOWN -> false
            }
        }.onFailure {
            Log.w(TAG, "execute ${device.kind} $action ${device.host}", it)
        }.getOrDefault(false)
    }

    fun probeHttp(host: String, port: Int): DeviceKind? {
        val base = "http://$host:$port"
        val wled = Http.probe("$base/json/info")
        if (wled != null && wled.contains("\"brand\"") && wled.contains("WLED")) return DeviceKind.WLED
        val shelly = Http.probe("$base/shelly") ?: Http.probe("$base/rpc/Shelly.GetDeviceInfo")
        if (shelly != null && (shelly.contains("\"type\"") || shelly.contains("\"id\"") || shelly.contains("Shelly"))) {
            return DeviceKind.SHELLY
        }
        val tasmota = Http.probe("$base/cm?cmnd=Status")
        if (tasmota != null && tasmota.contains("Status") && tasmota.contains("FriendlyName")) return DeviceKind.TASMOTA
        val hue = Http.probe("$base/api/0/config") ?: Http.probe("$base/api/config")
        if (hue != null && hue.contains("bridgeid") && hue.contains("Philips")) return DeviceKind.HUE
        val esphome = Http.probe(base)
        if (esphome != null && (esphome.contains("ESPHome", true) || esphome.contains("esphome", true))) {
            return DeviceKind.ESPHOME
        }
        return null
    }

    private fun entity(
        device: DeviceRow,
        suffix: String,
        kind: EntityKind,
        name: String,
        available: Boolean,
        on: Boolean?,
        brightness: Int? = null,
        extra: JSONObject = JSONObject()
    ) = EntityRow(
        id = "${device.id}:$suffix",
        deviceId = device.id,
        kind = kind.name,
        name = name,
        available = available,
        onState = on?.let { if (it) 1 else 0 },
        brightness = brightness,
        extraJson = extra.toString()
    )

    private fun refreshWled(device: DeviceRow): RefreshResult {
        val stateBody = Http.get("http://${device.host}:${device.port}/json/state")
        val available = stateBody != null
        val state = JSONObject(stateBody ?: "{}")
        val info = JSONObject(Http.get("http://${device.host}:${device.port}/json/info") ?: "{}")
        val on = if (available) state.optBoolean("on", false) else null
        val bri = if (available) state.optInt("bri", 0) else null
        val name = info.optString("name", device.name).ifBlank { device.name }
        return RefreshResult(
            listOf(entity(device, "light", EntityKind.LIGHT, name, available, on, bri))
        )
    }

    private fun execWled(device: DeviceRow, action: String, extra: JSONObject): Boolean {
        val body = JSONObject()
        when (action) {
            "turn_on" -> {
                body.put("on", true)
                if (extra.has("brightness")) body.put("bri", extra.getInt("brightness"))
            }
            "turn_off" -> body.put("on", false)
            "toggle" -> body.put("on", "t")
            "set_brightness" -> {
                body.put("on", true)
                body.put("bri", extra.optInt("brightness", 128))
            }
            else -> return false
        }
        return Http.postJson("http://${device.host}:${device.port}/json/state", body) != null
    }

    private fun refreshShelly(device: DeviceRow): RefreshResult {
        val gen2 = Http.exchange("http://${device.host}:${device.port}/rpc/Shelly.GetStatus")
        if (gen2.ok && !gen2.body.isNullOrBlank()) {
            val json = runCatching { JSONObject(gen2.body) }.getOrNull()
            if (json != null && ShellyJson.looksLikeGen2(json)) {
                val channels = ShellyJson.gen2Channels(json)
                if (channels.isNotEmpty()) return RefreshResult(channels.map { it.toEntity(device) })
            }
        }
        val gen1 = Http.exchange("http://${device.host}:${device.port}/status")
        if (gen1.ok && !gen1.body.isNullOrBlank()) {
            val json = runCatching { JSONObject(gen1.body) }.getOrNull()
            if (json != null && (ShellyJson.looksLikeGen1(json) || ShellyJson.gen1Channels(json).isNotEmpty())) {
                val channels = ShellyJson.gen1Channels(json)
                if (channels.isNotEmpty()) return RefreshResult(channels.map { it.toEntity(device) })
                return RefreshResult(listOf(entity(device, "relay0", EntityKind.SWITCH, device.name, true, false)))
            }
        }
        Log.i(TAG, "Shelly ${device.host} unreachable (gen2=${gen2.code} gen1=${gen1.code})")
        return RefreshResult(listOf(entity(device, "relay0", EntityKind.SWITCH, device.name, false, null)))
    }

    private fun ShellyJson.Channel.toEntity(device: DeviceRow) = entity(
        device,
        suffix,
        if (kind == "LIGHT") EntityKind.LIGHT else EntityKind.SWITCH,
        if (suffix.endsWith("0") && suffix.removeSuffix("0") in listOf("switch", "relay", "light")) device.name
        else "${device.name} ${suffix.filter { it.isDigit() }}",
        true,
        on,
        brightness
    )

    private fun execShelly(device: DeviceRow, entity: EntityRow, action: String): Boolean {
        val on = when (action) {
            "turn_on" -> true
            "turn_off" -> false
            "toggle" -> null
            else -> return false
        }
        val id = ShellyJson.channelIndex(entity.id.substringAfterLast(":"))
        val rpcBody = JSONObject()
            .put("id", 1)
            .put("method", if (entity.kind == EntityKind.LIGHT.name) "Light.Set" else "Switch.Set")
            .put("params", JSONObject().put("id", id.toInt()).put("on", on ?: JSONObject.NULL))
        val rpc = Http.postJsonCode("http://${device.host}:${device.port}/rpc", rpcBody)
        if (rpc.ok) return true
        Log.i(TAG, "Shelly gen2 Switch.Set ${rpc.code}, falling back to gen1 /relay")
        val turn = when (on) {
            true -> "on"
            false -> "off"
            null -> "toggle"
        }
        val preferred = if (entity.kind == EntityKind.LIGHT.name) "light" else "relay"
        val first = Http.exchange("http://${device.host}:${device.port}/$preferred/$id?turn=$turn")
        if (first.ok) return true
        val secondPath = if (preferred == "relay") "light" else "relay"
        val second = Http.exchange("http://${device.host}:${device.port}/$secondPath/$id?turn=$turn")
        return second.ok
    }

    private fun refreshTasmota(device: DeviceRow): RefreshResult {
        val body = Http.get("http://${device.host}:${device.port}/cm?cmnd=Status%200") ?: return RefreshResult(
            listOf(entity(device, "power", EntityKind.SWITCH, device.name, false, null))
        )
        val json = JSONObject(body)
        val status = json.optJSONObject("Status") ?: JSONObject()
        val sts = json.optJSONObject("StatusSTS") ?: JSONObject()
        val name = (status.optJSONArray("FriendlyName")?.optString(0)).takeUnless { it.isNullOrBlank() } ?: device.name
        val power = sts.optString("POWER", sts.optString("POWER1", "OFF")).equals("ON", true)
        val dimmer = sts.optInt("Dimmer", -1).takeIf { it >= 0 }
        val kind = if (dimmer != null) EntityKind.LIGHT else EntityKind.SWITCH
        val bri = dimmer?.let { (it * 255) / 100 }
        return RefreshResult(listOf(entity(device, "power", kind, name, true, power, bri)))
    }

    private fun execTasmota(device: DeviceRow, entity: EntityRow, action: String, extra: JSONObject): Boolean {
        val cmnd = when (action) {
            "turn_on" -> "Power On"
            "turn_off" -> "Power Off"
            "toggle" -> "Power Toggle"
            "set_brightness" -> "Dimmer ${((extra.optInt("brightness", 128) * 100) / 255)}"
            else -> return false
        }
        return Http.get("http://${device.host}:${device.port}/cm?cmnd=" + java.net.URLEncoder.encode(cmnd, "UTF-8")) != null
    }

    fun pairHue(host: String, port: Int): String? {
        val body = JSONObject().put("devicetype", "haphone#android")
        val raw = Http.postJson("http://$host:$port/api", body) ?: return null
        val array = JSONArray(raw)
        val first = array.optJSONObject(0) ?: return null
        return first.optJSONObject("success")?.optString("username")
    }

    private fun refreshHue(device: DeviceRow): RefreshResult {
        val extras = parseExtras(device.extrasJson)
        val user = extras.optString("username")
        if (user.isBlank()) {
            return RefreshResult(
                listOf(entity(device, "bridge", EntityKind.SENSOR, "${device.name} (press link button)", true, null))
            )
        }
        val raw = Http.get("http://${device.host}:${device.port}/api/$user/lights") ?: return RefreshResult(emptyList())
        val json = JSONObject(raw)
        val entities = mutableListOf<EntityRow>()
        json.keys().forEach { id ->
            val light = json.getJSONObject(id)
            val state = light.optJSONObject("state") ?: JSONObject()
            val bri = if (state.has("bri")) state.optInt("bri") else null
            entities += entity(
                device, "light$id", EntityKind.LIGHT, light.optString("name", "Light $id"),
                state.optBoolean("reachable", true), state.optBoolean("on"), bri,
                JSONObject().put("hueId", id)
            )
        }
        return RefreshResult(entities)
    }

    private fun execHue(device: DeviceRow, entity: EntityRow, action: String, extra: JSONObject): Boolean {
        val extras = parseExtras(device.extrasJson)
        val user = extras.optString("username")
        if (user.isBlank()) return false
        val hueId = parseExtras(entity.extraJson).optString("hueId", entity.id.substringAfter("light"))
        val body = JSONObject()
        when (action) {
            "turn_on" -> {
                body.put("on", true)
                if (extra.has("brightness")) body.put("bri", extra.getInt("brightness"))
            }
            "turn_off" -> body.put("on", false)
            "toggle" -> body.put("on", entity.onState != 1)
            "set_brightness" -> {
                body.put("on", true)
                body.put("bri", extra.optInt("brightness", 128))
            }
            else -> return false
        }
        val result = Http.putJson("http://${device.host}:${device.port}/api/$user/lights/$hueId/state", body)
        return result != null
    }

    private fun refreshKasa(device: DeviceRow): RefreshResult {
        val reply = KasaCodec.send(device.host, device.port, JSONObject().put("system", JSONObject().put("get_sysinfo", JSONObject())))
            ?: return RefreshResult(listOf(entity(device, "switch", EntityKind.SWITCH, device.name, false, null)))
        val info = reply.optJSONObject("system")?.optJSONObject("get_sysinfo") ?: JSONObject()
        val relay = info.optInt("relay_state", 0) == 1
        val name = info.optString("alias", device.name)
        val brightness = if (info.has("brightness")) info.optInt("brightness") else null
        val kind = if (brightness != null) EntityKind.LIGHT else EntityKind.SWITCH
        val bri255 = brightness?.let { (it * 255) / 100 }
        return RefreshResult(listOf(entity(device, "switch", kind, name, true, relay, bri255)))
    }

    private fun execKasa(device: DeviceRow, action: String): Boolean {
        val on = when (action) {
            "turn_on", "set_brightness" -> 1
            "turn_off" -> 0
            "toggle" -> null
            else -> return false
        }
        val state = on ?: run {
            val current = refreshKasa(device).entities.firstOrNull()?.onState ?: 0
            if (current == 1) 0 else 1
        }
        val cmd = JSONObject().put("system", JSONObject().put("set_relay_state", JSONObject().put("state", state)))
        return KasaCodec.send(device.host, device.port, cmd) != null
    }

    private fun refreshLifx(device: DeviceRow): RefreshResult {
        val power = LifxCodec.getPower(device.host, device.port)
        return RefreshResult(
            listOf(entity(device, "light", EntityKind.LIGHT, device.name, power != null, power == true, null))
        )
    }

    private fun execLifx(device: DeviceRow, action: String, extra: JSONObject): Boolean {
        val on = when (action) {
            "turn_on", "set_brightness" -> true
            "turn_off" -> false
            "toggle" -> !(refreshLifx(device).entities.firstOrNull()?.onState == 1)
            else -> return false
        }
        return LifxCodec.setPower(device.host, device.port, on)
    }

    private fun refreshRoku(device: DeviceRow): RefreshResult {
        val xml = Http.get("http://${device.host}:${device.port}/query/device-info")
        val name = Regex("<user-device-name>([^<]+)</user-device-name>").find(xml ?: "")?.groupValues?.get(1) ?: device.name
        val power = xml?.contains("<power-mode>PowerOn</power-mode>") == true
        return RefreshResult(listOf(entity(device, "player", EntityKind.MEDIA, name, xml != null, power)))
    }

    private fun execRoku(device: DeviceRow, action: String): Boolean {
        val key = when (action) {
            "turn_on" -> "PowerOn"
            "turn_off" -> "PowerOff"
            "toggle" -> "Power"
            "play" -> "Play"
            "pause" -> "Play"
            else -> return false
        }
        return Http.post("http://${device.host}:${device.port}/keypress/$key", "", "text/plain") != null ||
            runCatching {
                val conn = (URL("http://${device.host}:${device.port}/keypress/$key").openConnection() as HttpURLConnection)
                conn.requestMethod = "POST"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.doOutput = true
                conn.responseCode in 200..399
            }.getOrDefault(false)
    }

    private fun refreshSonos(device: DeviceRow): RefreshResult {
        val extras = parseExtras(device.extrasJson)
        val control = extras.optString("avTransport", "/MediaRenderer/AVTransport/Control")
        val xml = Upnp.soap(
            "http://${device.host}:${device.port}$control",
            "urn:schemas-upnp-org:service:AVTransport:1",
            "GetTransportInfo",
            "<InstanceID>0</InstanceID>"
        )
        val playing = xml?.contains("PLAYING") == true
        return RefreshResult(listOf(entity(device, "player", EntityKind.MEDIA, device.name, xml != null, playing)))
    }

    private fun execSonos(device: DeviceRow, action: String, extra: JSONObject): Boolean {
        val extras = parseExtras(device.extrasJson)
        val control = extras.optString("avTransport", "/MediaRenderer/AVTransport/Control")
        val rendering = extras.optString("rendering", "/MediaRenderer/RenderingControl/Control")
        return when (action) {
            "play", "turn_on" -> Upnp.soap(
                "http://${device.host}:${device.port}$control",
                "urn:schemas-upnp-org:service:AVTransport:1",
                "Play",
                "<InstanceID>0</InstanceID><Speed>1</Speed>"
            ) != null
            "pause", "turn_off" -> Upnp.soap(
                "http://${device.host}:${device.port}$control",
                "urn:schemas-upnp-org:service:AVTransport:1",
                "Pause",
                "<InstanceID>0</InstanceID>"
            ) != null
            "set_volume" -> Upnp.soap(
                "http://${device.host}:${device.port}$rendering",
                "urn:schemas-upnp-org:service:RenderingControl:1",
                "SetVolume",
                "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>${extra.optInt("volume", 20)}</DesiredVolume>"
            ) != null
            else -> false
        }
    }

    private fun refreshEsphome(device: DeviceRow): RefreshResult {
        val html = Http.get("http://${device.host}:${device.port}/") ?: return RefreshResult(
            listOf(entity(device, "node", EntityKind.SWITCH, device.name, false, null))
        )
        val extras = parseExtras(device.extrasJson)
        val path = extras.optString("entityPath").ifBlank { null }
        if (path != null) {
            val raw = Http.get("http://${device.host}:${device.port}$path")
            val on = raw?.contains("\"value\": true") == true || raw?.contains("ON") == true
            return RefreshResult(listOf(entity(device, "main", EntityKind.SWITCH, device.name, raw != null, on)))
        }
        return RefreshResult(
            listOf(entity(device, "node", EntityKind.SENSOR, device.name, true, null, extra = JSONObject().put("note", "Open in browser or set entity path")))
        )
    }

    private fun execEsphome(device: DeviceRow, entity: EntityRow, action: String): Boolean {
        val extras = parseExtras(device.extrasJson)
        val path = extras.optString("entityPath").ifBlank { return false }
        val suffix = when (action) {
            "turn_on" -> "/turn_on"
            "turn_off" -> "/turn_off"
            "toggle" -> "/toggle"
            else -> return false
        }
        return Http.post("http://${device.host}:${device.port}$path$suffix", "", "application/x-www-form-urlencoded") != null ||
            Http.get("http://${device.host}:${device.port}$path$suffix") != null
    }

    private fun refreshMediaPlaceholder(device: DeviceRow): RefreshResult {
        val available = device.host.isNotBlank()
        return RefreshResult(
            listOf(entity(device, "player", EntityKind.MEDIA, device.name, available, null, extra = JSONObject().put("note", "Discovered on LAN")))
        )
    }

    private fun refreshGeneric(device: DeviceRow): RefreshResult {
        val extras = parseExtras(device.extrasJson)
        if (GenericHttp.missingUrls(extras)) {
            return RefreshResult(listOf(entity(device, "main", EntityKind.SWITCH, device.name, false, null)))
        }
        val status = extras.optString("statusUrl")
        val body = if (status.isNotBlank()) Http.get(status.replace("{host}", device.host)) else null
        val available = if (status.isNotBlank()) body != null else true
        val on = GenericHttp.statusOn(body ?: if (status.isBlank()) "" else null, extras)
        return RefreshResult(listOf(entity(device, "main", EntityKind.SWITCH, device.name, available, on)))
    }

    private fun refreshMqtt(device: DeviceRow): RefreshResult {
        val extras = parseExtras(device.extrasJson)
        val stateTopic = extras.optString("stateTopic")
        val commandTopic = extras.optString("commandTopic")
        val onPayload = extras.optString("onPayload").ifBlank { "ON" }
        val kind = if (extras.optString("kind").equals("light", true)) EntityKind.LIGHT else EntityKind.SWITCH
        if (stateTopic.isBlank() && commandTopic.isBlank()) {
            return RefreshResult(listOf(entity(device, "main", kind, device.name, false, null)))
        }
        if (stateTopic.isNotBlank()) MqttBridge.subscribe(stateTopic)
        val last = if (stateTopic.isNotBlank()) MqttBridge.lastPayload(stateTopic) else null
        val connected = MqttBridge.isConnected()
        val on = when {
            last == null -> null
            last.equals(onPayload, true) || last == "1" || last.equals("true", true) -> true
            else -> false
        }
        val available = connected && (stateTopic.isBlank() || last != null || commandTopic.isNotBlank())
        return RefreshResult(listOf(entity(device, "main", kind, device.name, available, on)))
    }

    private fun execMqtt(device: DeviceRow, action: String): Boolean {
        val extras = parseExtras(device.extrasJson)
        val commandTopic = extras.optString("commandTopic")
        if (commandTopic.isBlank()) return false
        val onPayload = extras.optString("onPayload").ifBlank { "ON" }
        val offPayload = extras.optString("offPayload").ifBlank { "OFF" }
        val payload = when (action) {
            "turn_on" -> onPayload
            "turn_off" -> offPayload
            "toggle" -> {
                val last = extras.optString("stateTopic").let { MqttBridge.lastPayload(it) }
                if (last != null && last.equals(onPayload, true)) offPayload else onPayload
            }
            else -> return false
        }
        return MqttBridge.publish(commandTopic, payload)
    }

    private fun execGeneric(device: DeviceRow, action: String): Boolean {
        val extras = parseExtras(device.extrasJson)
        val url = when (action) {
            "turn_on" -> extras.optString("onUrl")
            "turn_off" -> extras.optString("offUrl")
            "toggle" -> extras.optString("toggleUrl").ifBlank { extras.optString("onUrl") }
            else -> ""
        }.replace("{host}", device.host)
        if (url.isBlank()) return false
        return Http.get(url) != null || Http.post(url, "") != null
    }

    private fun execDlna(device: DeviceRow, action: String): Boolean {
        val extras = parseExtras(device.extrasJson)
        val control = extras.optString("avTransport")
        if (control.isBlank()) return false
        val soapAction = when (action) {
            "play", "turn_on" -> "Play" to "<InstanceID>0</InstanceID><Speed>1</Speed>"
            "pause", "turn_off" -> "Pause" to "<InstanceID>0</InstanceID>"
            "stop" -> "Stop" to "<InstanceID>0</InstanceID>"
            else -> return false
        }
        return Upnp.soap(
            if (control.startsWith("http")) control else "http://${device.host}:${device.port}$control",
            "urn:schemas-upnp-org:service:AVTransport:1",
            soapAction.first,
            soapAction.second
        ) != null
    }
}

object KasaCodec {
    fun encrypt(payload: ByteArray, includeLength: Boolean): ByteArray {
        var key = 171
        val encoded = ByteArray(payload.size)
        for (i in payload.indices) {
            val b = payload[i].toInt() and 0xFF xor key
            key = b
            encoded[i] = b.toByte()
        }
        if (!includeLength) return encoded
        val out = ByteArray(4 + encoded.size)
        ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN).putInt(encoded.size)
        System.arraycopy(encoded, 0, out, 4, encoded.size)
        return out
    }

    fun decrypt(payload: ByteArray, skipLength: Boolean): ByteArray {
        val data = if (skipLength && payload.size >= 4) payload.copyOfRange(4, payload.size) else payload
        var key = 171
        val decoded = ByteArray(data.size)
        for (i in data.indices) {
            val unsigned = data[i].toInt() and 0xFF
            decoded[i] = (unsigned xor key).toByte()
            key = unsigned
        }
        return decoded
    }

    fun send(host: String, port: Int, command: JSONObject): JSONObject? {
        val packet = encrypt(command.toString().toByteArray(), includeLength = true)
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, if (port == 0) 9999 else port), 4000)
                socket.soTimeout = 4000
                val out = BufferedOutputStream(socket.getOutputStream())
                out.write(packet)
                out.flush()
                val input = BufferedInputStream(socket.getInputStream())
                val header = ByteArray(4)
                if (input.read(header) != 4) return null
                val size = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).int
                if (size <= 0 || size > 4096) return null
                val body = ByteArray(size)
                var read = 0
                while (read < size) {
                    val n = input.read(body, read, size - read)
                    if (n < 0) break
                    read += n
                }
                JSONObject(String(decrypt(body, skipLength = false)))
            }
        }.getOrNull()
    }
}

object LifxCodec {
    private fun header(type: Int, tagged: Boolean, target: ByteArray = ByteArray(8)): ByteArray {
        val buf = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(36.toShort())
        var protocol = 1024
        protocol = protocol or (1 shl 12) // addressable
        if (tagged) protocol = protocol or (1 shl 13)
        buf.putShort(protocol.toShort())
        buf.putInt(1)
        buf.put(target)
        buf.put(ByteArray(6))
        buf.put(0)
        buf.put(0)
        buf.putLong(0)
        buf.putShort(type.toShort())
        buf.putShort(0)
        return buf.array()
    }

    fun discoveryPacket(): ByteArray = header(2, tagged = true)

    fun setPower(host: String, port: Int, on: Boolean): Boolean {
        val packet = header(117, tagged = false) + ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(if (on) 65535.toShort() else 0)
            .putInt(0)
            .array()
        return send(host, port, packet) != null
    }

    fun getPower(host: String, port: Int): Boolean? {
        val reply = send(host, port, header(20, tagged = false)) ?: return null
        if (reply.size < 38) return null
        val level = ByteBuffer.wrap(reply, 36, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        return level > 0
    }

    private fun send(host: String, port: Int, packet: ByteArray): ByteArray? {
        return runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = 1500
                val addr = InetAddress.getByName(host)
                socket.send(DatagramPacket(packet, packet.size, addr, if (port == 0) 56700 else port))
                val buf = ByteArray(128)
                val incoming = DatagramPacket(buf, buf.size)
                socket.receive(incoming)
                incoming.data.copyOf(incoming.length)
            }
        }.getOrNull()
    }
}

object Upnp {
    fun soap(url: String, serviceType: String, action: String, innerXml: String): String? {
        val envelope = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:$action xmlns:u="$serviceType">$innerXml</u:$action>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("Content-Type", "text/xml; charset=\"utf-8\"")
            .header("SOAPACTION", "\"$serviceType#$action\"")
            .post(envelope.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()
        return runCatching {
            Http.client.newCall(request).execute().use { it.body?.string() }
        }.getOrNull()
    }
}

fun newId(): String = UUID.randomUUID().toString().substring(0, 8)
