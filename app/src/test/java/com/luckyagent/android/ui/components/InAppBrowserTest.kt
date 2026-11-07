package com.luckyagent.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InAppBrowserTest {
    @Test
    fun onlyHttpAndHttpsOpenInTheApp() {
        assertEquals("https://example.com/a", inAppBrowserUrl("https://example.com/a"))
        assertEquals("http://example.com", inAppBrowserUrl(" http://example.com "))
        assertNull(inAppBrowserUrl("geo:1,2"))
        assertNull(inAppBrowserUrl("mailto:a@b.c"))
        assertNull(inAppBrowserUrl("javascript:alert(1)"))
        assertNull(inAppBrowserUrl("https://"))
        assertNull(inAppBrowserUrl(""))
    }
}
