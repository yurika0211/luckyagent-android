package com.luckyagent.android.ui

import com.luckyagent.android.data.api.RuntimeSession
import com.luckyagent.android.data.api.SessionHistory
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRecapTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun oldServersRemainCompatible() {
        assertNull(json.decodeFromString<RuntimeSession>("""{"id":"old","title":"manual"}""").recap)
        assertNull(json.decodeFromString<SessionHistory>("""{"id":"old","messages":[]}""").recapUpdatedAt)
    }

    @Test fun listAndHistoryUseTheSameServerText() {
        val wire = """{"id":"s","title":"manual","recap":"我们修复了 foo.go。","recap_updated_at":"2026-10-27T03:00:00Z","messages":[]}"""
        val listing = json.decodeFromString<RuntimeSession>(wire)
        val history = json.decodeFromString<SessionHistory>(wire)
        assertEquals("manual", listing.title)
        assertEquals("我们修复了 foo.go。", history.recap)
        assertEquals(listing.recap, history.recap)
        assertEquals(listing.recapUpdatedAt, history.recapUpdatedAt)
    }

    @Test fun labelFollowsRecapLanguage() {
        assertEquals("回顾", recapLabel("你问了 foo.go。"))
        assertEquals("Recap", recapLabel("You asked about foo.go."))
        assertEquals("振り返り", recapLabel("コードを更新しました。"))
        assertEquals("돌아보기", recapLabel("코드를 수정했습니다."))
    }
}
