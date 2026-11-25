package io.github.coolcoderme.haphone

import io.github.coolcoderme.haphone.control.KasaCodec
import io.github.coolcoderme.haphone.control.LifxCodec
import io.github.coolcoderme.haphone.util.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test
    fun kasaRoundTrip() {
        val src = """{"system":{"get_sysinfo":{}}}""".toByteArray()
        val enc = KasaCodec.encrypt(src, includeLength = false)
        val dec = KasaCodec.decrypt(enc, skipLength = false)
        assertEquals(String(src), String(dec))
        val withLen = KasaCodec.encrypt(src, includeLength = true)
        assertEquals(src.size + 4, withLen.size)
        assertEquals(String(src), String(KasaCodec.decrypt(withLen, skipLength = true)))
    }

    @Test
    fun lifxDiscoveryHeaderIs36Bytes() {
        assertEquals(36, LifxCodec.discoveryPacket().size)
    }

    @Test
    fun ssdpParserReadsLocation() {
        val text = "HTTP/1.1 200 OK\r\nLOCATION: http://192.168.1.20:8060/\r\nST: roku:ecp\r\nSERVER: Roku/1.0\r\nUSN: uuid:roku\r\n\r\n"
        val parsed = Text.parseSsdp(text)!!
        assertEquals("http://192.168.1.20:8060/", parsed.location)
        assertEquals("roku:ecp", parsed.st)
        assertEquals("192.168.1.20", Text.hostOf(parsed.location))
        assertEquals(Integer.valueOf(8060), Text.portOf(parsed.location))
    }

    @Test
    fun slugSanitizesNames() {
        assertEquals("living_room", Text.slug("Living Room"))
        assertEquals("hue_bridge", Text.slug("Hue Bridge!!"))
        assertEquals("", Text.slug(""))
    }

    @Test
    fun kasaDoesNotLeavePlaintext() {
        val src = "hello".toByteArray()
        val enc = KasaCodec.encrypt(src, false)
        assertFalse(String(enc).contains("hello"))
        assertTrue(enc[0] != src[0])
    }
}
