package io.github.coolcoderme.haphone.util

import org.json.JSONObject

object Json {
    /**
     * Android's [JSONObject.optString] returns the literal `"null"` when the value is JSON null.
     * Treat missing, JSON null, blank, and the string `"null"` as no id.
     */
    fun optionalId(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.optString(key, "").trim()
        return value.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }
}
