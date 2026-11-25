package io.github.coolcoderme.haphone.control

import org.json.JSONObject

object GenericHttp {
    fun statusOn(body: String?, extras: JSONObject): Boolean? {
        if (body == null) return null
        val onHint = extras.optString("onContains")
        return if (onHint.isNotBlank()) body.contains(onHint) else true
    }

    fun missingUrls(extras: JSONObject): Boolean {
        val status = extras.optString("statusUrl")
        val onUrl = extras.optString("onUrl")
        val offUrl = extras.optString("offUrl")
        return status.isBlank() && onUrl.isBlank() && offUrl.isBlank()
    }
}
