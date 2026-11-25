package io.github.coolcoderme.haphone.control

import org.json.JSONObject

/**
 * MQTT extras are stored as camelCase (`commandTopic`, `stateTopic`).
 * Home Assistant's MQTT switch/light config uses snake_case
 * (`command_topic`, `state_topic`). Accept both and normalize on write.
 */
object MqttExtras {
    fun commandTopic(extras: JSONObject): String =
        first(extras, "commandTopic", "command_topic")

    fun stateTopic(extras: JSONObject): String =
        first(extras, "stateTopic", "state_topic")

    fun onPayload(extras: JSONObject): String =
        first(extras, "onPayload", "payload_on").ifBlank { "ON" }

    fun offPayload(extras: JSONObject): String =
        first(extras, "offPayload", "payload_off").ifBlank { "OFF" }

    fun normalize(extras: JSONObject): JSONObject {
        val out = JSONObject(extras.toString())
        copyAlias(out, "commandTopic", "command_topic")
        copyAlias(out, "stateTopic", "state_topic")
        copyAlias(out, "onPayload", "payload_on")
        copyAlias(out, "offPayload", "payload_off")
        return out
    }

    private fun first(extras: JSONObject, camel: String, snake: String): String {
        val camelVal = extras.optString(camel, "").trim()
        if (camelVal.isNotEmpty() && !camelVal.equals("null", ignoreCase = true)) return camelVal
        val snakeVal = extras.optString(snake, "").trim()
        return snakeVal.takeUnless { it.equals("null", ignoreCase = true) } ?: ""
    }

    private fun copyAlias(obj: JSONObject, camel: String, snake: String) {
        val value = first(obj, camel, snake)
        if (value.isNotEmpty()) obj.put(camel, value) else obj.remove(camel)
        obj.remove(snake)
    }
}
