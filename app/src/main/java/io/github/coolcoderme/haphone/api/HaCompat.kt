package io.github.coolcoderme.haphone.api

import io.github.coolcoderme.haphone.control.newId
import io.github.coolcoderme.haphone.data.HubRepository
import io.github.coolcoderme.haphone.net.Lan
import org.json.JSONArray
import org.json.JSONObject

object HaCompat {
    const val HA_VERSION = "2024.12.0"
    const val ACCESS_TOKEN = "local-owner-token"

    fun authRequired() = JSONObject().put("type", "auth_required").put("ha_version", HA_VERSION)
    fun authOk() = JSONObject().put("type", "auth_ok").put("ha_version", HA_VERSION)

    fun result(id: Int, payload: Any?): JSONObject {
        val msg = JSONObject().put("id", id).put("type", "result").put("success", true)
        if (payload == null) msg.put("result", JSONObject.NULL) else msg.put("result", payload)
        return msg
    }

    fun error(id: Int, code: String, message: String) = JSONObject()
        .put("id", id)
        .put("type", "result")
        .put("success", false)
        .put("error", JSONObject().put("code", code).put("message", message))

    fun event(id: Int, event: JSONObject) = JSONObject()
        .put("id", id)
        .put("type", "event")
        .put("event", event)

    fun pong(id: Int) = JSONObject().put("id", id).put("type", "pong")

    fun config(repo: HubRepository, internalUrl: String): JSONObject {
        val units = JSONObject()
            .put("length", "km")
            .put("accumulated_precipitation", "mm")
            .put("mass", "g")
            .put("pressure", "Pa")
            .put("temperature", "°C")
            .put("volume", "L")
            .put("wind_speed", "m/s")
        return JSONObject()
            .put("latitude", 0)
            .put("longitude", 0)
            .put("elevation", 0)
            .put("unit_system", units)
            .put("location_name", repo.prefs.hubName)
            .put("time_zone", "UTC")
            .put("components", JSONArray(listOf(
                "frontend", "http", "websocket_api", "api", "light", "switch",
                "media_player", "scene", "automation", "zone", "person", "config", "lovelace"
            )))
            .put("config_dir", "/config")
            .put("whitelist_external_dirs", JSONArray())
            .put("allowlist_external_dirs", JSONArray())
            .put("allowlist_external_urls", JSONArray())
            .put("version", HA_VERSION)
            .put("config_source", "storage")
            .put("recovery_mode", false)
            .put("state", "RUNNING")
            .put("external_url", JSONObject.NULL)
            .put("internal_url", internalUrl)
            .put("currency", "USD")
            .put("country", "US")
            .put("language", "en")
            .put("safe_mode", false)
            .put("debug", false)
            .put("radius", 100)
    }

    fun panels(): JSONObject {
        fun panel(component: String, icon: String?, title: String?, path: String, admin: Boolean = false) =
            JSONObject()
                .put("component_name", component)
                .put("icon", icon ?: JSONObject.NULL)
                .put("title", title ?: JSONObject.NULL)
                .put("config", if (component == "lovelace") JSONObject().put("mode", "storage") else JSONObject.NULL)
                .put("url_path", path)
                .put("require_admin", admin)

        return JSONObject()
            .put("lovelace", panel("lovelace", null, null, "lovelace"))
            .put("config", panel("config", "mdi:cog", "config", "config", true))
            .put("developer-tools", panel("developer-tools", "mdi:hammer", "developer_tools", "developer-tools", true))
            .put("profile", panel("profile", null, null, "profile"))
    }

    fun services(): JSONObject {
        fun svc(name: String, extra: JSONObject = JSONObject()) =
            JSONObject().put("name", name).put("description", "").put("fields", extra)
        val light = JSONObject()
            .put("turn_on", svc("Turn on"))
            .put("turn_off", svc("Turn off"))
            .put("toggle", svc("Toggle"))
        val toggle = JSONObject()
            .put("turn_on", svc("Turn on"))
            .put("turn_off", svc("Turn off"))
            .put("toggle", svc("Toggle"))
        val media = JSONObject()
            .put("media_play", svc("Play"))
            .put("media_pause", svc("Pause"))
            .put("turn_on", svc("Turn on"))
            .put("turn_off", svc("Turn off"))
            .put("volume_set", svc("Set volume"))
        val scene = JSONObject().put("turn_on", svc("Activate"))
        val automation = JSONObject()
            .put("turn_on", svc("Enable"))
            .put("turn_off", svc("Disable"))
            .put("toggle", svc("Toggle"))
            .put("trigger", svc("Trigger"))
        val homeassistant = JSONObject()
            .put("turn_on", svc("Turn on"))
            .put("turn_off", svc("Turn off"))
            .put("toggle", svc("Toggle"))
            .put("restart", svc("Restart"))
        return JSONObject()
            .put("light", light)
            .put("switch", toggle)
            .put("media_player", media)
            .put("scene", scene)
            .put("automation", automation)
            .put("homeassistant", homeassistant)
    }

    fun currentUser() = JSONObject()
        .put("id", "local-owner")
        .put("name", "Owner")
        .put("is_owner", true)
        .put("is_admin", true)
        .put("credentials", JSONArray().put(JSONObject().put("auth_provider_type", "trusted_networks").put("auth_provider_id", "local")))
        .put("mfa_modules", JSONArray())

    fun themes() = JSONObject()
        .put("themes", JSONObject())
        .put("default_theme", "default")
        .put("default_dark_theme", JSONObject.NULL)

    fun tokenResponse(hassUrl: String) = JSONObject()
        .put("access_token", ACCESS_TOKEN)
        .put("token_type", "Bearer")
        .put("refresh_token", "local-refresh")
        .put("expires_in", 315360000)
        .put("hassUrl", hassUrl)
        .put("clientId", "$hassUrl/")

    fun context() = JSONObject()
        .put("id", newId() + newId())
        .put("parent_id", JSONObject.NULL)
        .put("user_id", "local-owner")

    fun internalUrl(context: android.content.Context, port: Int): String {
        val ip = Lan.primaryIpv4(context) ?: "127.0.0.1"
        return "http://$ip:$port"
    }
}
