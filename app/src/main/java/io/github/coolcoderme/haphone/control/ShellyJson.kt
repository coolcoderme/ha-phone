package io.github.coolcoderme.haphone.control

import org.json.JSONObject

/** Pure JSON helpers for Shelly gen1 (`/status`) and gen2 (`/rpc/Shelly.GetStatus`). */
object ShellyJson {
    data class Channel(
        val suffix: String,
        val kind: String,
        val on: Boolean,
        val brightness: Int?
    )

    fun looksLikeGen2(json: JSONObject): Boolean {
        json.keys().forEach { key ->
            if (key.startsWith("switch:") || key.startsWith("light:") || key.startsWith("cover:")) {
                return true
            }
        }
        return false
    }

    fun looksLikeGen1(json: JSONObject): Boolean =
        json.has("relays") || json.has("lights") || json.has("rollers") || json.has("meters")

    fun gen2Channels(json: JSONObject): List<Channel> {
        val out = mutableListOf<Channel>()
        json.keys().forEach { key ->
            if (key.startsWith("switch:")) {
                val sw = json.optJSONObject(key) ?: return@forEach
                val id = key.substringAfter(":")
                out += Channel("switch$id", "SWITCH", sw.optBoolean("output", false), null)
            }
            if (key.startsWith("light:")) {
                val light = json.optJSONObject(key) ?: return@forEach
                val id = key.substringAfter(":")
                out += Channel(
                    "light$id",
                    "LIGHT",
                    light.optBoolean("output", false),
                    if (light.has("brightness")) light.optInt("brightness") else null
                )
            }
        }
        return out
    }

    fun gen1Channels(json: JSONObject): List<Channel> {
        val out = mutableListOf<Channel>()
        val relays = json.optJSONArray("relays")
        if (relays != null) {
            for (i in 0 until relays.length()) {
                val relay = relays.optJSONObject(i) ?: continue
                out += Channel("relay$i", "SWITCH", relay.optBoolean("ison"), null)
            }
        }
        val lights = json.optJSONArray("lights")
        if (lights != null) {
            for (i in 0 until lights.length()) {
                val light = lights.optJSONObject(i) ?: continue
                out += Channel(
                    "light$i",
                    "LIGHT",
                    light.optBoolean("ison"),
                    if (light.has("brightness")) light.optInt("brightness") else null
                )
            }
        }
        return out
    }

    fun channelIndex(entitySuffix: String): String =
        entitySuffix.filter { it.isDigit() }.ifBlank { "0" }
}
