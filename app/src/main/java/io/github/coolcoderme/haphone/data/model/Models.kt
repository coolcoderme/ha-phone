package io.github.coolcoderme.haphone.data.model

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceKind(val label: String, val autoAdd: Boolean) {
    WLED("WLED", true),
    SHELLY("Shelly", true),
    TASMOTA("Tasmota", true),
    HUE("Philips Hue", true),
    KASA("TP-Link Kasa", true),
    LIFX("LIFX", true),
    ROKU("Roku", true),
    SONOS("Sonos", true),
    ESPHOME("ESPHome", true),
    CHROMECAST("Chromecast", false),
    DLNA("DLNA / UPnP media", true),
    MQTT("MQTT", false),
    GENERIC_HTTP("Generic HTTP", false),
    UNKNOWN("Unknown", false);

    companion object {
        fun fromStorage(value: String): DeviceKind =
            entries.find { it.name == value } ?: UNKNOWN
    }
}

enum class EntityKind { LIGHT, SWITCH, MEDIA, SENSOR, BUTTON }

data class DiscoveredDevice(
    val kind: DeviceKind,
    val name: String,
    val host: String,
    val port: Int,
    val extras: Map<String, String> = emptyMap()
)

data class EntityAction(
    val name: String,
    val payload: JSONObject = JSONObject()
)

data class HubSnapshot(
    val devices: List<DeviceRecord>,
    val entities: List<EntityRecord>,
    val rooms: List<RoomRecord>,
    val scenes: List<SceneRecord>,
    val automations: List<AutomationRecord>
)

data class DeviceRecord(
    val id: String,
    val kind: DeviceKind,
    val name: String,
    val host: String,
    val port: Int,
    val extrasJson: String,
    val roomId: String?,
    val favorite: Boolean
)

data class EntityRecord(
    val id: String,
    val deviceId: String,
    val kind: EntityKind,
    val name: String,
    val available: Boolean,
    val on: Boolean?,
    val brightness: Int?,
    val extraJson: String
)

data class RoomRecord(
    val id: String,
    val name: String
)

data class SceneRecord(
    val id: String,
    val name: String,
    val actionsJson: String
)

data class AutomationRecord(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val triggerJson: String,
    val actionsJson: String,
    val lastFiredAt: Long
)

fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key, "")
    return value.ifBlank { null }
}

fun extrasOf(vararg pairs: Pair<String, String>): String {
    val obj = JSONObject()
    pairs.forEach { obj.put(it.first, it.second) }
    return obj.toString()
}

fun parseExtras(json: String): JSONObject = try {
    JSONObject(json.ifBlank { "{}" })
} catch (_: Exception) {
    JSONObject()
}

fun actionsFromJson(json: String): List<JSONObject> {
    return try {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getJSONObject(it) }
    } catch (_: Exception) {
        emptyList()
    }
}
