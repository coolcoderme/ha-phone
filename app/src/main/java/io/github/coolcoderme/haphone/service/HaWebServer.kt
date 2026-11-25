package io.github.coolcoderme.haphone.service

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import io.github.coolcoderme.haphone.api.HaCompat
import io.github.coolcoderme.haphone.control.Controllers
import io.github.coolcoderme.haphone.data.HubRepository
import io.github.coolcoderme.haphone.data.model.DeviceKind
import io.github.coolcoderme.haphone.data.model.DiscoveredDevice
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class HaWebServer(
    private val context: Context,
    private val repo: HubRepository,
    private val scope: CoroutineScope,
    port: Int,
    private val onScan: () -> Unit
) : NanoWSD(port) {
    private val sockets = CopyOnWriteArrayList<HubSocket>()

    fun broadcastEvent(event: JSONObject) {
        sockets.forEach { it.forwardEvent(event) }
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket = HubSocket(handshake)

    override fun serveHttp(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val method = session.method
        return try {
            when {
                uri == "/api/websocket" -> super.serveHttp(session)
                uri == "/auth/providers" && method == Method.GET ->
                    json(JSONArray().put(JSONObject().put("type", "trusted_networks").put("id", "local").put("name", "Local")))
                uri == "/auth/login_flow" && method == Method.POST -> authFlow()
                uri.startsWith("/auth/login_flow/") && method == Method.POST -> authFlow()
                uri == "/auth/token" && method == Method.POST -> {
                    readBody(session)
                    json(HaCompat.tokenResponse(HaCompat.internalUrl(context, listeningPort)))
                }
                uri == "/auth/authorize" -> serveAsset("www/index.html", "text/html")
                uri == "/api/" || uri == "/api" -> json(JSONObject().put("message", "API running.").put("version", HaCompat.HA_VERSION))
                uri == "/api/config" -> json(runBlocking { HaCompat.config(repo, HaCompat.internalUrl(context, listeningPort)) })
                uri == "/api/states" -> json(runBlocking { repo.haStates() })
                uri.startsWith("/api/states/") && method == Method.GET -> entityState(uri.substringAfter("/api/states/"))
                uri.startsWith("/api/services/") && method == Method.POST -> callServiceRest(uri, session)
                uri == "/api/discovery" -> discoveryJson()
                uri == "/api/discovery/scan" && method == Method.POST -> {
                    onScan()
                    json(JSONObject().put("ok", true))
                }
                uri == "/api/discovery/add" && method == Method.POST -> addDiscovered(session)
                uri == "/api/rooms" && method == Method.GET -> json(runBlocking {
                    JSONArray(repo.rooms().map { JSONObject().put("id", it.id).put("name", it.name) })
                })
                uri == "/api/rooms" && method == Method.POST -> upsertRoom(session)
                uri == "/api/scenes" && method == Method.GET -> json(runBlocking {
                    JSONArray(repo.scenes().map { JSONObject().put("id", it.id).put("name", it.name) })
                })
                uri == "/api/scenes" && method == Method.POST -> saveScene(session)
                uri.startsWith("/api/scenes/") && uri.endsWith("/activate") -> activateScene(uri)
                uri == "/api/automations" && method == Method.GET -> json(runBlocking {
                    JSONArray(repo.automations().map {
                        JSONObject().put("id", it.id).put("name", it.name).put("enabled", it.enabled)
                            .put("trigger", JSONObject(it.triggerJson.ifBlank { "{}" }))
                    })
                })
                uri == "/api/automations" && method == Method.POST -> saveAutomation(session)
                uri == "/api/devices" && method == Method.GET -> devicesJson()
                uri == "/api/devices/manual" && method == Method.POST -> addManual(session)
                uri.startsWith("/api/devices/") && uri.endsWith("/hue_pair") -> huePair(uri)
                uri.startsWith("/api/devices/") && method == Method.DELETE -> deleteDevice(uri)
                uri == "/api/backup" && method == Method.GET -> json(runBlocking { repo.exportBackup() })
                uri == "/api/restore" && method == Method.POST -> restore(session)
                uri == "/api/settings" && method == Method.GET -> settings()
                uri == "/api/settings" && method == Method.POST -> saveSettings(session)
                uri == "/" || uri == "/index.html" || !uri.startsWith("/api") -> staticFile(uri)
                else -> json(JSONObject().put("error", "not_found"), NanoHTTPD.Response.Status.NOT_FOUND)
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP $uri", e)
            json(JSONObject().put("error", e.message ?: "error"), NanoHTTPD.Response.Status.INTERNAL_ERROR)
        }
    }

    private fun staticFile(uri: String): Response {
        val official = assetExists("ha_frontend/index.html")
        val root = if (official) "ha_frontend" else "www"
        var path = if (uri == "/" || uri.isBlank()) "index.html" else uri.trimStart('/')
        if (path.contains("..")) path = "index.html"
        val assetPath = "$root/$path"
        if (assetExists(assetPath)) {
            return serveAsset(assetPath, mime(path))
        }
        if (official && assetExists("ha_frontend/index.html")) {
            return serveAsset("ha_frontend/index.html", "text/html")
        }
        return serveAsset("www/index.html", "text/html")
    }

    private fun assetExists(path: String): Boolean {
        return runCatching { context.assets.open(path).close(); true }.getOrDefault(false)
    }

    private fun serveAsset(path: String, mime: String): Response {
        val stream: InputStream = context.assets.open(path)
        val size = stream.available()
        val response = newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mime, stream, size.toLong())
        response.addHeader("Cache-Control", if (path.endsWith(".html")) "no-cache" else "public, max-age=86400")
        return response
    }

    private fun mime(path: String): String = when {
        path.endsWith(".html") -> "text/html"
        path.endsWith(".js") -> "application/javascript"
        path.endsWith(".css") -> "text/css"
        path.endsWith(".svg") -> "image/svg+xml"
        path.endsWith(".json") -> "application/json"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".woff2") -> "font/woff2"
        path.endsWith(".map") -> "application/json"
        else -> "application/octet-stream"
    }

    private fun json(payload: Any, status: NanoHTTPD.Response.Status = NanoHTTPD.Response.Status.OK): Response {
        val body = payload.toString()
        val bytes = body.toByteArray(Charset.forName("UTF-8"))
        val response = newFixedLengthResponse(
            status,
            "application/json; charset=utf-8",
            ByteArrayInputStream(bytes),
            bytes.size.toLong()
        )
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        runCatching { session.parseBody(files) }
        return files["postData"] ?: ""
    }

    private fun authFlow(): Response {
        return json(
            JSONObject()
                .put("type", "create_entry")
                .put("version", 1)
                .put("title", "Local")
                .put("data", JSONObject())
                .put("result", "local-owner")
        )
    }

    private fun entityState(entityId: String): Response {
        val states = runBlocking { repo.haStates() }
        for (i in 0 until states.length()) {
            val s = states.getJSONObject(i)
            if (s.optString("entity_id") == entityId) return json(s)
        }
        return json(JSONObject().put("message", "Entity not found."), NanoHTTPD.Response.Status.NOT_FOUND)
    }

    private fun callServiceRest(uri: String, session: IHTTPSession): Response {
        val parts = uri.trim('/').split('/')
        val domain = parts.getOrNull(2) ?: return json(JSONObject().put("error", "bad"), NanoHTTPD.Response.Status.BAD_REQUEST)
        val service = parts.getOrNull(3) ?: return json(JSONObject().put("error", "bad"), NanoHTTPD.Response.Status.BAD_REQUEST)
        val body = JSONObject(readBody(session).ifBlank { "{}" })
        val entityId = body.optString("entity_id").ifBlank {
            body.optJSONObject("target")?.optString("entity_id") ?: ""
        }
        val ok = runBlocking { repo.callHaService(domain, service, entityId.ifBlank { null }, body) }
        return json(JSONObject().put("ok", ok).put("context", HaCompat.context()))
    }

    private fun discoveryJson(): Response {
        val array = JSONArray()
        repo.discovered.forEach {
            array.put(JSONObject().put("kind", it.kind.name).put("name", it.name).put("host", it.host).put("port", it.port))
        }
        return json(array)
    }

    private fun addDiscovered(session: IHTTPSession): Response {
        val body = JSONObject(readBody(session).ifBlank { "{}" })
        val item = DiscoveredDevice(
            kind = DeviceKind.fromStorage(body.optString("kind")),
            name = body.optString("name"),
            host = body.optString("host"),
            port = body.optInt("port"),
            extras = emptyMap()
        )
        val row = runBlocking { repo.addDiscovered(item, body.optString("roomId").ifBlank { null }) }
        return json(JSONObject().put("id", row.id))
    }

    private fun addManual(session: IHTTPSession): Response {
        val body = JSONObject(readBody(session).ifBlank { "{}" })
        val extras = body.optJSONObject("extras") ?: JSONObject()
        val row = runBlocking {
            repo.addManual(
                DeviceKind.fromStorage(body.optString("kind")),
                body.optString("name", "Device"),
                body.optString("host"),
                body.optInt("port", 80),
                extras,
                body.optString("roomId").ifBlank { null }
            )
        }
        return json(JSONObject().put("id", row.id))
    }

    private fun devicesJson(): Response {
        val array = JSONArray()
        runBlocking {
            repo.devices().forEach { device ->
                val entities = JSONArray()
                repo.entities().filter { it.deviceId == device.id }.forEach { entity ->
                    entities.put(
                        JSONObject()
                            .put("id", entity.id)
                            .put("entity_id", HubRepository.haEntityId(entity))
                            .put("name", entity.name)
                            .put("kind", entity.kind)
                            .put("on", entity.onState)
                            .put("brightness", entity.brightness)
                            .put("available", entity.available)
                    )
                }
                array.put(
                    JSONObject()
                        .put("id", device.id)
                        .put("kind", device.kind)
                        .put("name", device.name)
                        .put("host", device.host)
                        .put("port", device.port)
                        .put("roomId", device.roomId ?: JSONObject.NULL)
                        .put("entities", entities)
                )
            }
        }
        return json(array)
    }

    private fun deleteDevice(uri: String): Response {
        val id = uri.removePrefix("/api/devices/").substringBefore("/")
        runBlocking { repo.deleteDevice(id) }
        return json(JSONObject().put("ok", true))
    }

    private fun huePair(uri: String): Response {
        val id = uri.removePrefix("/api/devices/").removeSuffix("/hue_pair").trim('/')
        val ok = runBlocking { repo.pairHue(id) }
        return json(JSONObject().put("ok", ok).put("message", if (ok) "Linked" else "Press the button on the Hue bridge and retry"))
    }

    private fun upsertRoom(session: IHTTPSession): Response {
        val body = JSONObject(readBody(session).ifBlank { "{}" })
        val row = runBlocking { repo.upsertRoom(body.optString("name"), body.optString("id").ifBlank { HubRepository.slug(body.optString("name")) }) }
        return json(JSONObject().put("id", row.id).put("name", row.name))
    }

    private fun saveScene(session: IHTTPSession): Response {
        val body = JSONObject(readBody(session).ifBlank { "{}" })
        val row = runBlocking { repo.saveScene(body.optString("name", "Scene")) }
        return json(JSONObject().put("id", row.id))
    }

    private fun activateScene(uri: String): Response {
        val id = uri.removePrefix("/api/scenes/").removeSuffix("/activate").trim('/')
        runBlocking { repo.activateScene(id) }
        return json(JSONObject().put("ok", true))
    }

    private fun saveAutomation(session: IHTTPSession): Response {
        val body = JSONObject(readBody(session).ifBlank { "{}" })
        val row = runBlocking {
            repo.saveAutomation(
                body.optString("name", "Automation"),
                body.optJSONObject("trigger") ?: JSONObject(),
                body.optJSONArray("actions") ?: JSONArray()
            )
        }
        return json(JSONObject().put("id", row.id))
    }

    private fun restore(session: IHTTPSession): Response {
        val body = JSONObject(readBody(session).ifBlank { "{}" })
        runBlocking { repo.importBackup(body) }
        return json(JSONObject().put("ok", true))
    }

    private fun settings(): Response {
        return json(
            JSONObject()
                .put("hub_name", repo.prefs.hubName)
                .put("autostart", repo.prefs.autostart)
                .put("web_dashboard", repo.prefs.webDashboard)
                .put("web_port", repo.prefs.webPort)
                .put("mqtt_broker", repo.prefs.mqttBroker)
                .put("mqtt_port", repo.prefs.mqttPort)
                .put("version", HaCompat.HA_VERSION)
                .put("ssid", io.github.coolcoderme.haphone.net.Lan.wifiSsid(context) ?: "")
                .put("url", HaCompat.internalUrl(context, listeningPort))
        )
    }

    private fun saveSettings(session: IHTTPSession): Response {
        val body = JSONObject(readBody(session).ifBlank { "{}" })
        if (body.has("hub_name")) repo.prefs.hubName = body.getString("hub_name")
        if (body.has("autostart")) repo.prefs.autostart = body.getBoolean("autostart")
        if (body.has("web_dashboard")) repo.prefs.webDashboard = body.getBoolean("web_dashboard")
        if (body.has("mqtt_broker")) repo.prefs.mqttBroker = body.getString("mqtt_broker")
        if (body.has("mqtt_port")) repo.prefs.mqttPort = body.getInt("mqtt_port")
        if (body.has("mqtt_user")) repo.prefs.mqttUser = body.getString("mqtt_user")
        if (body.has("mqtt_password")) repo.prefs.mqttPassword = body.getString("mqtt_password")
        return settings()
    }

    inner class HubSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        private val subscriptions = HashMap<Int, String>()
        private var authed = false

        override fun onOpen() {
            sockets.add(this)
            sendSafe(HaCompat.authRequired().toString())
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            sockets.remove(this)
        }

        override fun onMessage(message: WebSocketFrame) {
            val text = message.textPayload ?: return
            handle(text)
        }

        override fun onPong(pong: WebSocketFrame?) {}
        override fun onException(exception: java.io.IOException?) {
            Log.w(TAG, "ws error", exception)
        }

        fun forwardEvent(event: JSONObject) {
            subscriptions.filter { it.value == "state_changed" || it.value == "*" || it.value == "all" }
                .keys
                .forEach { sendSafe(HaCompat.event(it, event).toString()) }
        }

        private fun handle(text: String) {
            val msg = JSONObject(text)
            val type = msg.optString("type")
            if (!authed) {
                if (type == "auth") {
                    authed = true
                    sendSafe(HaCompat.authOk().toString())
                } else {
                    sendSafe(JSONObject().put("type", "auth_invalid").put("message", "Need auth").toString())
                }
                return
            }
            val id = msg.optInt("id")
            scope.launch(Dispatchers.IO) {
                val reply = dispatch(id, type, msg)
                sendSafe(reply.toString())
            }
        }

        private suspend fun dispatch(id: Int, type: String, msg: JSONObject): JSONObject {
            return try {
                when (type) {
                    "ping" -> HaCompat.pong(id)
                    "supported_features" -> HaCompat.result(id, null)
                    "get_states" -> HaCompat.result(id, repo.haStates())
                    "get_config" -> HaCompat.result(id, HaCompat.config(repo, HaCompat.internalUrl(context, listeningPort)))
                    "get_services" -> HaCompat.result(id, HaCompat.services())
                    "get_panels" -> HaCompat.result(id, HaCompat.panels())
                    "auth/current_user", "get_user" -> HaCompat.result(id, HaCompat.currentUser())
                    "frontend/get_themes" -> HaCompat.result(id, HaCompat.themes())
                    "frontend/set_theme" -> HaCompat.result(id, null)
                    "frontend/get_translations" -> HaCompat.result(id, JSONObject().put("resources", JSONObject()))
                    "lovelace/config" -> HaCompat.result(id, repo.lovelaceConfig())
                    "lovelace/config/save" -> HaCompat.result(id, null)
                    "lovelace/resources" -> HaCompat.result(id, JSONArray())
                    "lovelace/dashboards/list" -> HaCompat.result(id, JSONArray())
                    "config/entity_registry/list" -> HaCompat.result(id, repo.entityRegistry())
                    "config/entity_registry/list_for_display" -> HaCompat.result(id, repo.entityRegistryForDisplay())
                    "config/device_registry/list" -> HaCompat.result(id, repo.deviceRegistry())
                    "config/area_registry/list" -> HaCompat.result(id, repo.areaRegistry())
                    "config/floor_registry/list" -> HaCompat.result(id, JSONArray())
                    "config/label_registry/list" -> HaCompat.result(id, JSONArray())
                    "config_entries/get" -> HaCompat.result(id, JSONArray())
                    "persistent_notification/get" -> HaCompat.result(id, JSONArray())
                    "person/list" -> HaCompat.result(id, JSONArray().put(JSONObject().put("id", "local-owner").put("name", "Owner").put("user_id", "local-owner")))
                    "manifest/list" -> HaCompat.result(id, JSONArray())
                    "analytics" -> HaCompat.result(id, JSONObject())
                    "subscribe_events" -> {
                        subscriptions[id] = msg.optString("event_type", "*")
                        HaCompat.result(id, null)
                    }
                    "subscribe_entities" -> {
                        subscriptions[id] = "state_changed"
                        val added = JSONObject()
                        val states = repo.haStates()
                        for (i in 0 until states.length()) {
                            val s = states.getJSONObject(i)
                            added.put(
                                s.getString("entity_id"),
                                JSONObject()
                                    .put("s", s.optString("state"))
                                    .put("a", s.optJSONObject("attributes") ?: JSONObject())
                                    .put("c", s.optJSONObject("context") ?: JSONObject())
                                    .put("lc", System.currentTimeMillis() / 1000.0)
                                    .put("lu", System.currentTimeMillis() / 1000.0)
                            )
                        }
                        sendSafe(HaCompat.event(id, JSONObject().put("a", added)).toString())
                        HaCompat.result(id, null)
                    }
                    "unsubscribe_events" -> {
                        subscriptions.remove(msg.optInt("subscription"))
                        HaCompat.result(id, null)
                    }
                    "call_service" -> {
                        val domain = msg.optString("domain")
                        val service = msg.optString("service")
                        val data = msg.optJSONObject("service_data") ?: JSONObject()
                        val target = msg.optJSONObject("target")
                        var entityId = data.optString("entity_id")
                        if (entityId.isBlank()) entityId = target?.optString("entity_id") ?: ""
                        if (entityId.isBlank() && data.optJSONArray("entity_id") != null) {
                            entityId = data.getJSONArray("entity_id").optString(0)
                        }
                        repo.callHaService(domain, service, entityId.ifBlank { null }, data)
                        HaCompat.result(id, JSONObject().put("context", HaCompat.context()))
                    }
                    "camera/thumbnail", "media_player_thumbnail" -> HaCompat.error(id, "not_found", "No camera")
                    else -> {
                        Log.i(TAG, "unhandled ws $type")
                        if (type.contains("list") || type.contains("get")) HaCompat.result(id, JSONArray())
                        else HaCompat.result(id, null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "dispatch $type", e)
                HaCompat.error(id, "unknown_error", e.message ?: "error")
            }
        }

        private fun sendSafe(text: String) {
            runCatching { send(text) }
        }
    }

    companion object {
        private const val TAG = "HaWebServer"
    }
}
