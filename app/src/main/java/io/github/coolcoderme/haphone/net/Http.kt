package io.github.coolcoderme.haphone.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        val builder = Request.Builder().url(url)
        headers.forEach { builder.header(it.key, it.value) }
        return runCatching {
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }

    fun getCode(url: String): Pair<Int, String?> {
        return runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                response.code to response.body?.string()
            }
        }.getOrDefault(-1 to null)
    }

    fun post(url: String, body: String, contentType: String = "application/json"): String? {
        val media = contentType.toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(media))
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }

    fun putJson(url: String, json: JSONObject): String? {
        val request = Request.Builder()
            .url(url)
            .put(json.toString().toRequestBody(jsonType))
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                response.body?.string()
            }
        }.getOrNull()
    }

    fun postJson(url: String, json: JSONObject): String? =
        post(url, json.toString(), "application/json")
}

object Lan {
    fun ipv4Addresses(): List<String> {
        val found = mutableListOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        found += addr.hostAddress ?: continue
                    }
                }
            }
        }
        return found
    }

    fun primaryIpv4(context: Context): String? {
        val fromInterfaces = ipv4Addresses().firstOrNull { !it.startsWith("169.254.") }
        if (fromInterfaces != null) return fromInterfaces
        return runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ip = wm.connectionInfo?.ipAddress ?: return null
            if (ip == 0) return null
            String.format(
                "%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff
            )
        }.getOrNull()
    }

    fun wifiSsid(context: Context): String? {
        return runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        }.getOrNull()
    }

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        return cm.activeNetworkInfo?.isConnected == true
    }
}
