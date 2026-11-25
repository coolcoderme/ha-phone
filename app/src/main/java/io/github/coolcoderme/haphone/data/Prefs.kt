package io.github.coolcoderme.haphone.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val p: SharedPreferences =
        context.getSharedPreferences("haphone", Context.MODE_PRIVATE)

    var hubName: String
        get() = p.getString("hub_name", "ha-phone") ?: "ha-phone"
        set(value) { p.edit().putString("hub_name", value).apply() }

    var autostart: Boolean
        get() = p.getBoolean("autostart", true)
        set(value) { p.edit().putBoolean("autostart", value).apply() }

    var webDashboard: Boolean
        get() = p.getBoolean("web_dashboard", true)
        set(value) { p.edit().putBoolean("web_dashboard", value).apply() }

    var webPort: Int
        get() = p.getInt("web_port", 8123)
        set(value) { p.edit().putInt("web_port", value).apply() }

    var pollSeconds: Int
        get() = p.getInt("poll_seconds", 15)
        set(value) { p.edit().putInt("poll_seconds", value).apply() }

    var mqttBroker: String
        get() = p.getString("mqtt_broker", "") ?: ""
        set(value) { p.edit().putString("mqtt_broker", value).apply() }

    var mqttPort: Int
        get() = p.getInt("mqtt_port", 1883)
        set(value) { p.edit().putInt("mqtt_port", value).apply() }

    var mqttUser: String
        get() = p.getString("mqtt_user", "") ?: ""
        set(value) { p.edit().putString("mqtt_user", value).apply() }

    var mqttPassword: String
        get() = p.getString("mqtt_password", "") ?: ""
        set(value) { p.edit().putString("mqtt_password", value).apply() }

    var firstLaunchDone: Boolean
        get() = p.getBoolean("first_launch_done", false)
        set(value) { p.edit().putBoolean("first_launch_done", value).apply() }

    /** Empty means the LAN API is open (no PIN). Off by default. */
    var lanPin: String
        get() = p.getString("lan_pin", "") ?: ""
        set(value) { p.edit().putString("lan_pin", value).apply() }

    /** auto / dark / light */
    var theme: String
        get() = p.getString("theme", "auto") ?: "auto"
        set(value) { p.edit().putString("theme", value).apply() }
}
