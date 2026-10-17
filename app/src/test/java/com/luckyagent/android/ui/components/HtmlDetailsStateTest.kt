package com.luckyagent.android.ui.components

import org.junit.Assert.*
import org.junit.Test
import androidx.compose.runtime.saveable.SaverScope

class HtmlDetailsStateTest {
    private fun nodes(html: String) = parseHtml(html)!!
    private fun block(body: String, attributes: String = "") = "<details $attributes><summary>详情</summary><p>$body</p></details>"

    @Test fun sameTitleSiblingsRetainIndependentStateAcrossInsertionAndReordering() {
        val state = HtmlDetailsState()
        val initial = state.identities(nodes(block("first") + block("second"))).values.toSet()
        assertEquals(2, initial.size)
        val firstNodes = nodes(block("first") + block("second"))
        val ids = state.identities(firstNodes)
        val first = ids.getValue(firstNodes[0] as HtmlNode.Element)
        val second = ids.getValue(firstNodes[1] as HtmlNode.Element)
        state.toggle(second, false)

        val updated = nodes(block("new") + block("second") + block("first"))
        val next = state.identities(updated)
        assertEquals(second, next[updated[1]])
        assertEquals(first, next[updated[2]])
        assertTrue(state.isExpanded(next.getValue(updated[1] as HtmlNode.Element), false))
        assertFalse(state.isExpanded(next.getValue(updated[0] as HtmlNode.Element), false))
    }

    @Test fun nestedDetailsSurviveAnInsertedAncestorSiblingAndArbitraryBodyEdits() {
        val state = HtmlDetailsState()
        val old = state.identities(nodes("<div>" + block("first body") + "</div>")).values.single()
        state.toggle(old, false)
        val next = state.identities(nodes("<p>inserted block</p><div>" + block("updated body") + "</div>")).values.single()
        assertEquals(old, next)
        assertTrue(state.isExpanded(next, false))
    }

    @Test fun identicalSummaryAndBodyStillReceiveDistinctIdentities() {
        val state = HtmlDetailsState()
        val list = nodes(block("same") + block("same"))
        val ids = state.identities(list)
        assertNotEquals(ids[list[0]], ids[list[1]])
        state.toggle(ids.getValue(list[0] as HtmlNode.Element), false)
        assertFalse(state.isExpanded(ids.getValue(list[1] as HtmlNode.Element), false))
    }

    @Test fun explicitIdSurvivesSummaryAndBodyChanges() {
        val state = HtmlDetailsState()
        val initial = state.identities(nodes(block("first", "id='stable'"))).values.single()
        val updated = state.identities(nodes("<details id='stable'><summary>renamed</summary>different</details>")).values.single()
        assertEquals(initial, updated)
    }

    @Test fun streamingAppendDoesNotTransferStateToANewSameTitleSibling() {
        val state = HtmlDetailsState()
        val id = state.identities(nodes(block("streaming body"))).values.single()
        state.toggle(id, false)
        val nextNodes = nodes(block("new sibling") + block("streaming body continued"))
        val next = state.identities(nextNodes)
        assertEquals(id, next[nextNodes[1]])
        assertNotEquals(id, next[nextNodes[0]])
    }

    @Test fun savedExpansionStateRestoresTheSameNodeIdentities() {
        val state = HtmlDetailsState()
        val html = block("first") + block("second")
        val parsed = nodes(html)
        val id = state.identities(parsed).getValue(parsed[1] as HtmlNode.Element)
        state.toggle(id, false)
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(HtmlDetailsState.Saver) { scope.save(state) }!!
        val restored = HtmlDetailsState.Saver.restore(saved)!!
        val next = nodes(html)
        val restoredId = restored.identities(next).getValue(next[1] as HtmlNode.Element)
        assertEquals(id, restoredId)
        assertTrue(restored.isExpanded(restoredId, false))
    }
}
