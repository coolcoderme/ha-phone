package io.github.coolcoderme.haphone.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

object Http {
    private const val TAG = "HaPhoneHttp"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
        .addNetworkInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Connection", "close")
                .build()
            chain.proceed(request)
        }
        .build()

    data class Result(val code: Int, val body: String?) {
        val ok: Boolean get() = code in 200..299
    }

    fun exchange(
        url: String,
        method: String = "GET",
        body: String? = null,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap()
    ): Result {
        return runCatching {
            val builder = Request.Builder().url(url)
            headers.forEach { builder.header(it.key, it.value) }
            when (method) {
                "GET" -> builder.get()
                "POST" -> builder.post((body ?: "").toRequestBody(contentType.toMediaType()))
                "PUT" -> builder.put((body ?: "").toRequestBody(contentType.toMediaType()))
                else -> builder.method(method, body?.toRequestBody(contentType.toMediaType()))
            }
            client.newCall(builder.build()).execute().use { response ->
                // Always consume the body so a 404 does not stall the next call.
                Result(response.code, response.body?.string())
            }
        }.onFailure {
            Log.w(TAG, "$method $url failed", it)
        }.getOrDefault(Result(-1, null))
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        val result = exchange(url, headers = headers)
        return if (result.ok) result.body else null
    }

    fun getCode(url: String): Pair<Int, String?> {
        val result = exchange(url)
        return result.code to result.body
    }

    fun post(url: String, body: String, contentType: String = "application/json"): String? {
        val result = exchange(url, method = "POST", body = body, contentType = contentType)
        return if (result.ok) result.body else null
    }

    fun postCode(url: String, body: String, contentType: String = "application/json"): Result =
        exchange(url, method = "POST", body = body, contentType = contentType)

    fun putJson(url: String, json: JSONObject): String? {
        val result = exchange(url, method = "PUT", body = json.toString(), contentType = "application/json")
        return if (result.ok) result.body else result.body
    }

    fun postJson(url: String, json: JSONObject): String? =
        post(url, json.toString(), "application/json")

    fun postJsonCode(url: String, json: JSONObject): Result =
        postCode(url, json.toString(), "application/json")

    private val probeClient: OkHttpClient = client.newBuilder()
        .connectTimeout(400, TimeUnit.MILLISECONDS)
        .readTimeout(400, TimeUnit.MILLISECONDS)
        .writeTimeout(400, TimeUnit.MILLISECONDS)
        .build()

    fun probe(url: String): String? {
        return runCatching {
            probeClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful) body else null
            }
        }.getOrNull()
    }
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
