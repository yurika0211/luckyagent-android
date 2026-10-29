package com.luckyagent.android.ui.screens

import com.luckyagent.android.data.settings.ClientSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstConnectionTest {
    @Test fun savedKeyStillWaitsUntilThisSessionAuthenticates() {
        val fresh = ClientSettings(apiBase = "http://10.0.2.2:9090", apiKey = "")
        val saved = fresh.copy(apiKey = "secret")
        assertTrue(needsConnectionSetup(fresh, ComputerLink.Unknown))
        assertTrue(needsConnectionSetup(saved, ComputerLink.Unknown))
        assertTrue(needsConnectionSetup(saved, ComputerLink.Failed))
        assertFalse(needsConnectionSetup(saved, ComputerLink.Ready))
    }

    @Test fun rejectedKeyGetsADifferentNextStepFromAnUnreachableComputer() {
        assertTrue(connectionFailureMessage(IllegalStateException("sessions 401: api key required")).contains("密钥"))
        assertTrue(connectionFailureMessage(IllegalStateException("failed to connect")).contains("没能连上"))
    }

    @Test fun standaloneChatDoesNotUseTheComputerGate() {
        val standalone = ClientSettings(connectionMode = "standalone", apiBase = "", apiKey = "")
        assertFalse(needsConnectionSetup(standalone, ComputerLink.Failed))
    }

    @Test fun manualEntryInfersBearerAndRejectsABareHost() {
        assertNull(manualConnection("192.168.1.8:9090", "secret"))
        assertNull(manualConnection("http://192.168.1.8:9090", "  "))
        val key = manualConnection("http://192.168.1.8:9090/", " secret ")
        assertEquals("http://192.168.1.8:9090", key?.apiBase)
        assertEquals("secret", key?.apiKey)
        assertEquals(false, key?.useBearer)
        val bearer = manualConnection("https://agent.example", "Bearer token")
        assertEquals(true, bearer?.useBearer)
        assertEquals("token", bearer?.apiKey)
    }

    @Test fun successNamesTheComputerInsteadOfTheWordDefault() {
        assertEquals("书房电脑", connectionHostLabel("http://192.168.1.8:9090", "书房电脑 · 24h"))
        assertEquals("192.168.1.8", connectionHostLabel("http://192.168.1.8:9090", "Default"))
        assertEquals("这台电脑", connectionHostLabel("", "Runtime"))
    }
}
