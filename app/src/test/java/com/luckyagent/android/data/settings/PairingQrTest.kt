package com.luckyagent.android.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PairingQrTest {
    @Test
    fun parse_readsLanUrlAndTemporaryKey() {
        val raw = """
            {"v":1,"url":"http://192.168.1.8:9090","token":"lp_test","expires_at":"2026-10-09T04:00:00Z","ttl_hours":24,"name":"desk"}
        """.trimIndent()
        val parsed = PairingQr.parse(raw)
        val endpoint = parsed.toEndpoint("endpoint-qr")
        assertEquals("http://192.168.1.8:9090", endpoint.apiBase)
        assertEquals("lp_test", endpoint.apiKey)
        assertEquals("desk · 24h", endpoint.name)
        assertFalse(endpoint.useBearer)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_rejectsOtherQr() {
        PairingQr.parse("https://example.com")
    }
}
