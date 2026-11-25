package io.github.coolcoderme.haphone.automation

import io.github.coolcoderme.haphone.data.HubRepository
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class AutomationEngine(
    private val repo: HubRepository,
    private val scope: CoroutineScope
) {
    fun start() {
        scope.launch {
            while (isActive) {
                tick()
                delay(15_000)
            }
        }
        scope.launch {
            repo.events.collect { event ->
                if (event.optString("event_type") == "state_changed") {
                    onState(event.optJSONObject("data") ?: JSONObject())
                }
            }
        }
    }

    private suspend fun tick() {
        val now = Calendar.getInstance()
        val hhmm = String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        val dayStamp = now.get(Calendar.DAY_OF_YEAR)
        repo.automations().forEach { row ->
            if (!row.enabled) return@forEach
            val trigger = JSONObject(row.triggerJson.ifBlank { "{}" })
            if (trigger.optString("platform") != "time" && trigger.optString("type") != "time") return@forEach
            val at = trigger.optString("at", trigger.optString("time"))
            if (at != hhmm) return@forEach
            val last = Calendar.getInstance().apply { timeInMillis = row.lastFiredAt }
            if (row.lastFiredAt > 0 && last.get(Calendar.DAY_OF_YEAR) == dayStamp &&
                last.get(Calendar.YEAR) == now.get(Calendar.YEAR)
            ) return@forEach
            repo.runAutomationActions(row.id)
        }
    }

    private suspend fun onState(data: JSONObject) {
        val entityId = data.optString("entity_id")
        val newState = data.optJSONObject("new_state")?.optString("state") ?: return
        val oldState = data.optJSONObject("old_state")?.optString("state")
        repo.automations().forEach { row ->
            if (!row.enabled) return@forEach
            val trigger = JSONObject(row.triggerJson.ifBlank { "{}" })
            val type = trigger.optString("platform", trigger.optString("type"))
            if (type != "state") return@forEach
            val target = trigger.optString("entity_id")
            if (target.isNotBlank() && target != entityId) return@forEach
            val to = trigger.optString("to")
            val from = trigger.optString("from")
            if (to.isNotBlank() && to != newState) return@forEach
            if (from.isNotBlank() && from != oldState) return@forEach
            repo.runAutomationActions(row.id)
        }
    }
}
