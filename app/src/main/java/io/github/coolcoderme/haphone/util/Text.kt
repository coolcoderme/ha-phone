package io.github.coolcoderme.haphone.util

import java.util.Locale

object Text {
    fun slug(value: String): String =
        value.lowercase(Locale.US).replace("[^a-z0-9]+".toRegex(), "_").trim('_')

    fun formatTrigger(platform: String, at: String = "", entityId: String = "", to: String = ""): String =
        when (platform) {
            "time" -> "At ${at.ifBlank { "?" }}"
            "state" -> "${entityId.ifBlank { "entity" }} becomes ${to.ifBlank { "any" }}"
            else -> platform
        }

    data class SsdpMessage(val location: String, val st: String, val server: String, val usn: String)

    fun parseSsdp(text: String): SsdpMessage? {
        val headers = HashMap<String, String>()
        text.split("\r\n").forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                headers[line.substring(0, idx).trim().lowercase(Locale.US)] = line.substring(idx + 1).trim()
            }
        }
        val location = headers["location"] ?: return null
        return SsdpMessage(
            location = location,
            st = headers["st"] ?: "",
            server = headers["server"] ?: "",
            usn = headers["usn"] ?: ""
        )
    }

    fun hostOf(url: String): String? {
        return try {
            val without = url.substringAfter("://")
            without.substringBefore("/").substringBefore(":").ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    fun portOf(url: String): Int? {
        val hostPart = url.substringAfter("://").substringBefore("/")
        val port = hostPart.substringAfter(":", "")
        return port.toIntOrNull()
    }
}
