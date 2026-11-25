package io.github.coolcoderme.haphone

import io.github.coolcoderme.haphone.api.HaCompat
import io.github.coolcoderme.haphone.control.GenericHttp
import io.github.coolcoderme.haphone.control.ShellyJson
import io.github.coolcoderme.haphone.data.HubRepository
import io.github.coolcoderme.haphone.data.db.EntityRow
import io.github.coolcoderme.haphone.util.Text
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugfixTest {
    @Test
    fun slugDoesNotInventEntityForBlank() {
        assertEquals("", Text.slug(""))
        assertEquals("", Text.slug("???"))
        assertEquals("living_room", Text.slug("Living Room"))
    }

    @Test
    fun shellyGen1ParsesRelays() {
        val json = JSONObject("""{"relays":[{"ison":true},{"ison":false}]}""")
        assertTrue(ShellyJson.looksLikeGen1(json))
        assertFalse(ShellyJson.looksLikeGen2(json))
        val channels = ShellyJson.gen1Channels(json)
        assertEquals(2, channels.size)
        assertEquals("relay0", channels[0].suffix)
        assertTrue(channels[0].on)
        assertFalse(channels[1].on)
    }

    @Test
    fun shellyGen2ParsesSwitch() {
        val json = JSONObject("""{"switch:0":{"output":true},"sys":{}}""")
        assertTrue(ShellyJson.looksLikeGen2(json))
        val channels = ShellyJson.gen2Channels(json)
        assertEquals(1, channels.size)
        assertEquals("switch0", channels[0].suffix)
        assertEquals("SWITCH", channels[0].kind)
        assertTrue(channels[0].on)
    }

    @Test
    fun shellyChannelIndexStripsPrefix() {
        assertEquals("0", ShellyJson.channelIndex("relay0"))
        assertEquals("1", ShellyJson.channelIndex("switch1"))
        assertEquals("0", ShellyJson.channelIndex("switch"))
    }

    @Test
    fun genericHttpMissingUrls() {
        assertTrue(GenericHttp.missingUrls(JSONObject()))
        assertFalse(GenericHttp.missingUrls(JSONObject().put("onUrl", "http://x/on")))
        assertEquals(true, GenericHttp.statusOn("ok", JSONObject()))
        assertEquals(true, GenericHttp.statusOn("POWER ON", JSONObject().put("onContains", "ON")))
        assertEquals(false, GenericHttp.statusOn("off", JSONObject().put("onContains", "ON")))
        assertEquals(null, GenericHttp.statusOn(null, JSONObject()))
    }

    @Test
    fun lastChangedStaysWhenStateUnchanged() {
        val old = EntityRow(
            id = "a", deviceId = "d", kind = "LIGHT", name = "Lamp",
            available = true, onState = 1, brightness = 200, extraJson = "{}",
            lastChangedAt = 1000L, lastUpdatedAt = 1000L
        )
        val (changed, updated) = HubRepository.stamp(old, 1, 200, true, 5000L)
        assertEquals(1000L, changed)
        assertEquals(5000L, updated)
        val (changed2, updated2) = HubRepository.stamp(old, 0, 200, true, 5000L)
        assertEquals(5000L, changed2)
        assertEquals(5000L, updated2)
    }

    @Test
    fun servicesDescriptorListsLightTurnOn() {
        val services = HaCompat.services()
        assertTrue(services.has("light"))
        assertTrue(services.getJSONObject("light").has("turn_on"))
        assertTrue(services.has("switch"))
    }

    @Test
    fun automationTriggerCopy() {
        assertEquals("At 07:00", Text.formatTrigger("time", at = "07:00"))
        assertEquals("light.x becomes on", Text.formatTrigger("state", entityId = "light.x", to = "on"))
    }

    @Test
    fun isoMillisIsUtcZ() {
        val text = HubRepository.isoMillis(0L)
        assertTrue(text.startsWith("1970-01-01T"))
        assertTrue(text.endsWith("Z"))
    }
}
