package io.github.coolcoderme.haphone.data

import io.github.coolcoderme.haphone.control.Controllers
import io.github.coolcoderme.haphone.control.newId
import io.github.coolcoderme.haphone.data.db.AppDatabase
import io.github.coolcoderme.haphone.data.db.AutomationRow
import io.github.coolcoderme.haphone.data.db.DeviceRow
import io.github.coolcoderme.haphone.data.db.EntityRow
import io.github.coolcoderme.haphone.data.db.RoomRow
import io.github.coolcoderme.haphone.data.db.SceneRow
import io.github.coolcoderme.haphone.data.model.DeviceKind
import io.github.coolcoderme.haphone.data.model.DiscoveredDevice
import io.github.coolcoderme.haphone.data.model.EntityKind
import io.github.coolcoderme.haphone.data.model.actionsFromJson
import io.github.coolcoderme.haphone.data.model.parseExtras
import io.github.coolcoderme.haphone.util.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class HubRepository(
    private val db: AppDatabase,
    val prefs: Prefs
) {
    private val dao = db.dao()
    val events = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    val discovered = CopyOnWriteArrayList<DiscoveredDevice>()
    private val deviceMutexes = ConcurrentHashMap<String, Mutex>()

    private fun mutexFor(id: String) = deviceMutexes.getOrPut(id) { Mutex() }

    fun observeDevices() = dao.observeDevices()
    fun observeEntities() = dao.observeEntities()
    fun observeRooms() = dao.observeRooms()
    fun observeScenes() = dao.observeScenes()
    fun observeAutomations() = dao.observeAutomations()

    suspend fun devices() = dao.devices()
    suspend fun entities() = dao.entities()
    suspend fun rooms() = dao.rooms()
    suspend fun scenes() = dao.scenes()
    suspend fun automations() = dao.automations()

    suspend fun ensureDefaults() {
        if (dao.rooms().isEmpty()) {
            dao.upsertRoom(RoomRow(id = "living_room", name = "Living Room"))
            dao.upsertRoom(RoomRow(id = "bedroom", name = "Bedroom"))
            dao.upsertRoom(RoomRow(id = "kitchen", name = "Kitchen"))
        }
    }

    suspend fun addDiscovered(item: DiscoveredDevice, roomId: String? = null): DeviceRow {
        val existing = dao.devices().find { it.host == item.host && it.kind == item.kind.name }
        if (existing != null) return existing
        val extras = JSONObject()
        item.extras.forEach { extras.put(it.key, it.value) }
        val row = DeviceRow(
            id = newId(),
            kind = item.kind.name,
            name = item.name,
            host = item.host,
            port = item.port,
            extrasJson = extras.toString(),
            roomId = roomId,
            favorite = true
        )
        dao.upsertDevice(row)
        refreshDevice(row.id)
        return row
    }

    suspend fun addManual(
        kind: DeviceKind,
        name: String,
        host: String,
        port: Int,
        extras: JSONObject = JSONObject(),
        roomId: String? = null
    ): DeviceRow {
        val trimmedName = name.trim()
        val trimmedHost = host.trim()
        require(trimmedName.isNotBlank()) { "name required" }
        if (kind != DeviceKind.MQTT) {
            require(trimmedHost.isNotBlank()) { "host required" }
        }
        require(port in 0..65535) { "port out of range" }
        val row = DeviceRow(
            id = newId(),
            kind = kind.name,
            name = trimmedName,
            host = trimmedHost,
            port = if (port == 0 && kind != DeviceKind.KASA && kind != DeviceKind.LIFX && kind != DeviceKind.MQTT) 80 else port,
            extrasJson = extras.toString(),
            roomId = roomId,
            favorite = true
        )
        dao.upsertDevice(row)
        refreshDevice(row.id)
        return row
    }

    suspend fun rememberDiscovery(item: DiscoveredDevice) {
        val key = item.kind.name + "|" + item.host + "|" + item.port
        if (discovered.any { it.kind.name + "|" + it.host + "|" + it.port == key }) return
        discovered.add(item)
        events.tryEmit(JSONObject().put("event_type", "haphone_discovered").put("data", JSONObject()
            .put("kind", item.kind.name)
            .put("name", item.name)
            .put("host", item.host)
            .put("port", item.port)))
    }

    suspend fun deleteDevice(id: String) {
        dao.deleteEntitiesForDevice(id)
        dao.deleteDevice(id)
    }

    suspend fun setRoom(deviceId: String, roomId: String?) {
        val device = dao.device(deviceId) ?: return
        dao.upsertDevice(device.copy(roomId = roomId))
    }

    suspend fun upsertRoom(name: String, id: String = slug(name)): RoomRow {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "name required" }
        val roomId = id.ifBlank { slug(trimmed) }
        require(roomId.isNotBlank()) { "name required" }
        val row = RoomRow(id = roomId, name = trimmed)
        dao.upsertRoom(row)
        return row
    }

    suspend fun deleteRoom(id: String) = dao.deleteRoom(id)

    suspend fun saveScene(name: String, id: String? = null, actionsJson: String? = null): SceneRow {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "name required" }
        val existing = id?.let { wanted -> dao.scenes().find { it.id == wanted } }
        val actions = when {
            actionsJson != null -> actionsJson
            existing != null -> existing.actionsJson
            else -> {
                val array = JSONArray()
                dao.entities().forEach { entity ->
                    val obj = JSONObject()
                        .put("entity_id", haEntityId(entity))
                        .put("action", if (entity.onState == 1) "turn_on" else "turn_off")
                    if (entity.brightness != null) obj.put("brightness", entity.brightness)
                    array.put(obj)
                }
                array.toString()
            }
        }
        val row = SceneRow(id = existing?.id ?: id ?: newId(), name = trimmed, actionsJson = actions)
        dao.upsertScene(row)
        return row
    }

    suspend fun deleteScene(id: String) = dao.deleteScene(id)

    suspend fun activateScene(id: String) {
        val scene = dao.scenes().find { it.id == id } ?: return
        actionsFromJson(scene.actionsJson).forEach { action ->
            callHaService(
                domain = haEntityIdToDomain(action.optString("entity_id")),
                service = action.optString("action", "turn_on"),
                entityId = action.optString("entity_id"),
                data = action
            )
        }
    }

    suspend fun saveAutomation(
        name: String,
        trigger: JSONObject,
        actions: JSONArray,
        enabled: Boolean = true,
        id: String? = null
    ): AutomationRow {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "name required" }
        val existing = id?.let { wanted -> dao.automations().find { it.id == wanted } }
        val row = AutomationRow(
            id = existing?.id ?: id ?: newId(),
            name = trimmed,
            enabled = enabled,
            triggerJson = trigger.toString(),
            actionsJson = actions.toString(),
            lastFiredAt = existing?.lastFiredAt ?: 0L
        )
        dao.upsertAutomation(row)
        return row
    }

    suspend fun deleteAutomation(id: String) = dao.deleteAutomation(id)

    suspend fun setAutomationEnabled(id: String, enabled: Boolean) {
        val row = dao.automations().find { it.id == id } ?: return
        dao.upsertAutomation(row.copy(enabled = enabled))
    }

    suspend fun markAutomationFired(id: String) {
        val row = dao.automations().find { it.id == id } ?: return
        dao.upsertAutomation(row.copy(lastFiredAt = System.currentTimeMillis()))
    }

    suspend fun refreshAll() {
        dao.devices().forEach { refreshDevice(it.id) }
    }

    suspend fun refreshDevice(id: String) {
        mutexFor(id).withLock { refreshDeviceLocked(id) }
    }

    private suspend fun refreshDeviceLocked(id: String) {
        val device = dao.device(id) ?: return
        val result = Controllers.refresh(device) ?: return
        if (result.extrasPatch.length() > 0) {
            val extras = parseExtras(device.extrasJson)
            result.extrasPatch.keys().forEach { extras.put(it, result.extrasPatch.get(it)) }
            dao.upsertDevice(device.copy(extrasJson = extras.toString()))
        }
        val previous = dao.entitiesForDevice(device.id).associateBy { it.id }
        val now = System.currentTimeMillis()
        val newIds = result.entities.map { it.id }.toSet()
        previous.values.filter { it.id !in newIds }.forEach { dao.deleteEntity(it.id) }
        result.entities.forEach { incoming ->
            val old = previous[incoming.id]
            val (changedAt, updatedAt) = stamp(old, incoming.onState, incoming.brightness, incoming.available, now)
            val entity = incoming.copy(lastChangedAt = changedAt, lastUpdatedAt = updatedAt)
            dao.upsertEntity(entity)
            if (old == null || old.onState != entity.onState || old.brightness != entity.brightness || old.available != entity.available) {
                emitStateChanged(old, entity)
            }
        }
    }

    suspend fun pairHue(deviceId: String): Boolean {
        val device = dao.device(deviceId) ?: return false
        val user = Controllers.pairHue(device.host, device.port) ?: return false
        val extras = parseExtras(device.extrasJson).put("username", user)
        dao.upsertDevice(device.copy(extrasJson = extras.toString()))
        refreshDevice(deviceId)
        return true
    }

    suspend fun callHaService(domain: String, service: String, entityId: String?, data: JSONObject): Boolean {
        if (domain == "scene" && service == "turn_on") {
            val id = entityId?.substringAfter("scene.") ?: data.optString("entity_id").substringAfter("scene.")
            val scene = dao.scenes().find { it.id == id || slug(it.name) == id } ?: return false
            activateScene(scene.id)
            return true
        }
        if (domain == "automation") {
            val id = entityId?.substringAfter("automation.") ?: return false
            when (service) {
                "turn_on" -> setAutomationEnabled(id, true)
                "turn_off" -> setAutomationEnabled(id, false)
                "toggle" -> {
                    val row = dao.automations().find { it.id == id } ?: return false
                    setAutomationEnabled(id, !row.enabled)
                }
                "trigger" -> runAutomationActions(id)
            }
            return true
        }
        val entity = findEntity(entityId) ?: return false
        val device = dao.device(entity.deviceId) ?: return false
        val action = when (service) {
            "turn_on" -> "turn_on"
            "turn_off" -> "turn_off"
            "toggle" -> "toggle"
            "play_media", "media_play", "play" -> "play"
            "media_pause", "pause" -> "pause"
            "volume_set" -> "set_volume"
            else -> service
        }
        val extra = JSONObject()
        if (data.has("brightness")) extra.put("brightness", data.getInt("brightness"))
        if (data.has("brightness_pct")) extra.put("brightness", (data.getInt("brightness_pct") * 255) / 100)
        if (data.has("volume_level")) extra.put("volume", (data.getDouble("volume_level") * 100).toInt())
        return mutexFor(device.id).withLock {
            val ok = Controllers.execute(device, entity, action, extra)
            refreshDeviceLocked(device.id)
            ok
        }
    }

    suspend fun runAutomationActions(id: String) {
        val row = dao.automations().find { it.id == id } ?: return
        actionsFromJson(row.actionsJson).forEach { action ->
            callHaService(
                domain = action.optString("domain", haEntityIdToDomain(action.optString("entity_id"))),
                service = action.optString("service", action.optString("action", "turn_on")),
                entityId = action.optString("entity_id"),
                data = action.optJSONObject("data") ?: action
            )
        }
        markAutomationFired(id)
    }

    suspend fun findEntity(entityId: String?): EntityRow? {
        if (entityId.isNullOrBlank()) return null
        return dao.entities().find { haEntityId(it) == entityId || it.id == entityId }
    }

    suspend fun haStates(): JSONArray {
        val array = JSONArray()
        dao.entities().forEach { array.put(toHaState(it, dao.device(it.deviceId))) }
        dao.scenes().forEach { scene ->
            array.put(
                baseState("scene.${scene.id}", "scening", scene.name, "scene")
            )
        }
        dao.automations().forEach { automation ->
            array.put(
                baseState(
                    "automation.${automation.id}",
                    if (automation.enabled) "on" else "off",
                    automation.name,
                    "automation"
                )
            )
        }
        return array
    }

    suspend fun toHaState(entity: EntityRow, device: DeviceRow?): JSONObject {
        val domain = haDomain(entity)
        val state = when {
            entity.kind == EntityKind.SENSOR.name -> parseExtras(entity.extraJson).optString("state", "unknown")
            entity.onState == null -> "unavailable"
            entity.onState == 1 -> if (domain == "media_player") "playing" else "on"
            else -> if (domain == "media_player") "idle" else "off"
        }
        val attributes = JSONObject()
            .put("friendly_name", entity.name)
            .put("icon", haIcon(entity, device))
        if (entity.brightness != null) attributes.put("brightness", entity.brightness)
        if (device != null) {
            attributes.put("device_kind", device.kind)
            attributes.put("host", device.host)
        }
        if (entity.kind == EntityKind.LIGHT.name) {
            attributes.put("supported_color_modes", JSONArray().put("brightness"))
            attributes.put("color_mode", if (entity.onState == 1) "brightness" else null)
            attributes.put("supported_features", 1)
        }
        if (entity.kind == EntityKind.MEDIA.name) {
            attributes.put("supported_features", 512 + 256 + 128 + 64)
        }
        val changed = if (entity.lastChangedAt > 0) isoMillis(entity.lastChangedAt) else isoNow()
        val updated = if (entity.lastUpdatedAt > 0) isoMillis(entity.lastUpdatedAt) else changed
        return baseState(
            haEntityId(entity),
            if (entity.available) state else "unavailable",
            entity.name,
            domain,
            attributes,
            lastChanged = changed,
            lastUpdated = updated
        )
    }

    private fun baseState(
        entityId: String,
        state: String,
        name: String,
        domain: String,
        attributes: JSONObject = JSONObject().put("friendly_name", name),
        lastChanged: String = isoNow(),
        lastUpdated: String = lastChanged
    ): JSONObject {
        return JSONObject()
            .put("entity_id", entityId)
            .put("state", state)
            .put("attributes", attributes)
            .put("last_changed", lastChanged)
            .put("last_updated", lastUpdated)
            .put("context", JSONObject().put("id", newId()).put("parent_id", JSONObject.NULL).put("user_id", JSONObject.NULL))
    }

    suspend fun mqttStateTopics(): List<String> =
        dao.devices().filter { it.kind == DeviceKind.MQTT.name }
            .map { parseExtras(it.extrasJson).optString("stateTopic") }
            .filter { it.isNotBlank() }

    suspend fun refreshMqttByTopic(topic: String) {
        dao.devices().filter { it.kind == DeviceKind.MQTT.name }.forEach { device ->
            val extras = parseExtras(device.extrasJson)
            if (extras.optString("stateTopic") == topic) refreshDevice(device.id)
        }
    }

    suspend fun entityRegistry(): JSONArray {
        val array = JSONArray()
        dao.entities().forEach { entity ->
            val device = dao.device(entity.deviceId)
            array.put(
                JSONObject()
                    .put("entity_id", haEntityId(entity))
                    .put("name", entity.name)
                    .put("original_name", entity.name)
                    .put("unique_id", entity.id)
                    .put("platform", device?.kind?.lowercase(Locale.US) ?: "haphone")
                    .put("config_entry_id", device?.id)
                    .put("device_id", device?.id)
                    .put("area_id", device?.roomId)
                    .put("disabled_by", JSONObject.NULL)
                    .put("hidden_by", JSONObject.NULL)
                    .put("icon", JSONObject.NULL)
                    .put("has_entity_name", false)
            )
        }
        return array
    }

    suspend fun entityRegistryForDisplay(): JSONObject {
        val entities = JSONArray()
        dao.entities().forEach { entity ->
            val device = dao.device(entity.deviceId)
            entities.put(
                JSONObject()
                    .put("ei", haEntityId(entity))
                    .put("pl", device?.kind?.lowercase(Locale.US) ?: "haphone")
                    .put("lb", JSONArray())
                    .put("ai", device?.roomId ?: JSONObject.NULL)
                    .put("di", device?.id)
                    .put("en", entity.name)
                    .put("hn", false)
            )
        }
        return JSONObject()
            .put("entity_categories", JSONObject())
            .put("entities", entities)
    }

    suspend fun deviceRegistry(): JSONArray {
        val array = JSONArray()
        dao.devices().forEach { device ->
            array.put(
                JSONObject()
                    .put("id", device.id)
                    .put("name", device.name)
                    .put("name_by_user", JSONObject.NULL)
                    .put("manufacturer", manufacturer(device.kind))
                    .put("model", device.kind)
                    .put("area_id", device.roomId)
                    .put("config_entries", JSONArray().put(device.id))
                    .put("connections", JSONArray())
                    .put("identifiers", JSONArray().put(JSONArray().put("haphone").put(device.id)))
                    .put("sw_version", JSONObject.NULL)
                    .put("hw_version", JSONObject.NULL)
                    .put("via_device_id", JSONObject.NULL)
                    .put("disabled_by", JSONObject.NULL)
                    .put("entry_type", JSONObject.NULL)
            )
        }
        return array
    }

    suspend fun areaRegistry(): JSONArray {
        val array = JSONArray()
        dao.rooms().forEach { room ->
            array.put(
                JSONObject()
                    .put("area_id", room.id)
                    .put("name", room.name)
                    .put("picture", JSONObject.NULL)
                    .put("aliases", JSONArray())
                    .put("icon", JSONObject.NULL)
            )
        }
        return array
    }

    suspend fun lovelaceConfig(): JSONObject {
        val viewCards = JSONArray()
        val byRoom = dao.devices().groupBy { it.roomId }
        val entityByDevice = dao.entities().groupBy { it.deviceId }
        dao.rooms().forEach { room ->
            val entities = JSONArray()
            byRoom[room.id].orEmpty().forEach { device ->
                entityByDevice[device.id].orEmpty().forEach { entities.put(haEntityId(it)) }
            }
            if (entities.length() > 0) {
                viewCards.put(
                    JSONObject()
                        .put("type", "entities")
                        .put("title", room.name)
                        .put("show_header_toggle", true)
                        .put("entities", entities)
                )
            }
        }
        val unassigned = JSONArray()
        byRoom[null].orEmpty().forEach { device ->
            entityByDevice[device.id].orEmpty().forEach { unassigned.put(haEntityId(it)) }
        }
        if (unassigned.length() > 0) {
            viewCards.put(
                JSONObject().put("type", "entities").put("title", "Unassigned").put("entities", unassigned)
            )
        }
        val scenes = JSONArray()
        dao.scenes().forEach { scenes.put("scene.${it.id}") }
        if (scenes.length() > 0) {
            viewCards.put(JSONObject().put("type", "entities").put("title", "Scenes").put("entities", scenes))
        }
        if (viewCards.length() == 0) {
            viewCards.put(
                JSONObject()
                    .put("type", "markdown")
                    .put("content", "No devices yet. Open **Settings** and scan the Wi-Fi network.")
            )
        }
        return JSONObject()
            .put("title", prefs.hubName)
            .put(
                "views",
                JSONArray().put(
                    JSONObject()
                        .put("path", "default_view")
                        .put("title", "Home")
                        .put("cards", viewCards)
                )
            )
    }

    suspend fun exportBackup(): JSONObject {
        return JSONObject()
            .put("hub_name", prefs.hubName)
            .put("devices", JSONArray(dao.devices().map { JSONObject(it.toJson()) }))
            .put("entities", JSONArray(dao.entities().map { JSONObject(it.toJson()) }))
            .put("rooms", JSONArray(dao.rooms().map { JSONObject().put("id", it.id).put("name", it.name) }))
            .put("scenes", JSONArray(dao.scenes().map {
                JSONObject().put("id", it.id).put("name", it.name).put("actionsJson", it.actionsJson)
            }))
            .put("automations", JSONArray(dao.automations().map {
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("enabled", it.enabled)
                    .put("triggerJson", it.triggerJson)
                    .put("actionsJson", it.actionsJson)
            }))
    }

    suspend fun importBackup(json: JSONObject) {
        dao.clearAutomations()
        dao.clearScenes()
        dao.clearEntities()
        dao.clearDevices()
        dao.clearRooms()
        json.optJSONArray("rooms")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                dao.upsertRoom(RoomRow(o.getString("id"), o.getString("name")))
            }
        }
        json.optJSONArray("devices")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                dao.upsertDevice(
                    DeviceRow(
                        o.getString("id"), o.getString("kind"), o.getString("name"),
                        o.getString("host"), o.getInt("port"), o.optString("extrasJson", "{}"),
                        if (o.isNull("roomId")) null else o.optString("roomId"),
                        o.optBoolean("favorite", true)
                    )
                )
            }
        }
        json.optJSONArray("scenes")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                dao.upsertScene(SceneRow(o.getString("id"), o.getString("name"), o.optString("actionsJson", "[]")))
            }
        }
        json.optJSONArray("automations")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                dao.upsertAutomation(
                    AutomationRow(
                        o.getString("id"), o.getString("name"), o.optBoolean("enabled", true),
                        o.optString("triggerJson", "{}"), o.optString("actionsJson", "[]"), 0L
                    )
                )
            }
        }
        json.optString("hub_name").takeIf { it.isNotBlank() }?.let { prefs.hubName = it }
        refreshAll()
    }

    private fun DeviceRow.toJson() = JSONObject()
        .put("id", id).put("kind", kind).put("name", name).put("host", host)
        .put("port", port).put("extrasJson", extrasJson).put("roomId", roomId)
        .put("favorite", favorite).toString()

    private fun EntityRow.toJson() = JSONObject()
        .put("id", id).put("deviceId", deviceId).put("kind", kind).put("name", name)
        .put("available", available).put("onState", onState).put("brightness", brightness)
        .put("extraJson", extraJson).toString()

    private suspend fun emitStateChanged(old: EntityRow?, entity: EntityRow) {
        val device = dao.device(entity.deviceId)
        val event = JSONObject()
            .put("event_type", "state_changed")
            .put(
                "data",
                JSONObject()
                    .put("entity_id", haEntityId(entity))
                    .put("old_state", if (old == null) JSONObject.NULL else toHaState(old, device))
                    .put("new_state", toHaState(entity, device))
            )
            .put("origin", "LOCAL")
            .put("time_fired", isoNow())
            .put("context", JSONObject().put("id", newId()).put("parent_id", JSONObject.NULL).put("user_id", JSONObject.NULL))
        events.tryEmit(event)
    }

    companion object {
        fun slug(value: String): String = Text.slug(value)

        fun stamp(
            old: EntityRow?,
            newOn: Int?,
            newBri: Int?,
            newAvailable: Boolean,
            now: Long
        ): Pair<Long, Long> {
            val changed = old == null ||
                old.onState != newOn ||
                old.brightness != newBri ||
                old.available != newAvailable
            val lastChanged = if (!changed && old != null && old.lastChangedAt > 0) old.lastChangedAt else now
            return lastChanged to now
        }

        fun isoMillis(epochMs: Long): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            return fmt.format(Date(epochMs))
        }

        fun haDomain(entity: EntityRow): String = when (entity.kind) {
            EntityKind.LIGHT.name -> "light"
            EntityKind.SWITCH.name, EntityKind.BUTTON.name -> "switch"
            EntityKind.MEDIA.name -> "media_player"
            else -> "sensor"
        }

        fun haEntityId(entity: EntityRow): String = "${haDomain(entity)}.${slug(entity.name + "_" + entity.id.take(4))}"

        fun haEntityIdToDomain(entityId: String): String = entityId.substringBefore(".", "homeassistant")

        fun haIcon(entity: EntityRow, device: DeviceRow?): String {
            return when (device?.kind) {
                DeviceKind.WLED.name -> "mdi:led-strip-variant"
                DeviceKind.HUE.name -> "mdi:lightbulb"
                DeviceKind.SONOS.name, DeviceKind.CHROMECAST.name, DeviceKind.DLNA.name -> "mdi:speaker"
                DeviceKind.ROKU.name -> "mdi:television"
                DeviceKind.KASA.name, DeviceKind.SHELLY.name, DeviceKind.TASMOTA.name -> "mdi:power-socket"
                DeviceKind.LIFX.name -> "mdi:lightbulb-on"
                DeviceKind.MQTT.name -> "mdi:broadcast"
                else -> if (entity.kind == EntityKind.LIGHT.name) "mdi:lightbulb" else "mdi:toggle-switch"
            }
        }

        fun manufacturer(kind: String) = when (DeviceKind.fromStorage(kind)) {
            DeviceKind.WLED -> "WLED"
            DeviceKind.SHELLY -> "Shelly"
            DeviceKind.TASMOTA -> "Tasmota"
            DeviceKind.HUE -> "Signify"
            DeviceKind.KASA -> "TP-Link"
            DeviceKind.LIFX -> "LIFX"
            DeviceKind.ROKU -> "Roku"
            DeviceKind.SONOS -> "Sonos"
            DeviceKind.ESPHOME -> "ESPHome"
            DeviceKind.CHROMECAST -> "Google"
            DeviceKind.MQTT -> "MQTT"
            else -> "Local"
        }

        fun isoNow(): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            return fmt.format(Date())
        }
    }
}
