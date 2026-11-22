package com.luckyagent.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luckyagent.android.data.api.ApprovalOption
import com.luckyagent.android.data.api.PendingApproval
import com.luckyagent.android.ui.theme.CloverTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApprovalCardInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longInputPromptWrapsInNarrowLargeTextAndActionsRemainReachable() {
        val prompt = "请先阅读完整问题，再填写你希望保留的行为和需要修改的部分。".repeat(3) +
            "\n请逐项说明使用场景、已有约束和验收方式。\n最后请确认是否需要保留原来的输入内容。"
        show(inputApproval(prompt), fontScale = 1.4f)

        val layout = textLayout(prompt)
        assertTrue("The full question must wrap instead of using a one-line preview", layout.lineCount > 1)
        assertFalse("The question must not be ellipsized or clipped", layout.hasVisualOverflow)
        assertEquals(prompt.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        compose.onNodeWithText("提交").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
    }

    @Test fun inputDraftSurvivesCollapseAndIsSubmittedUnchanged() {
        val prompt = "请填写需要修改的内容。"
        var submitted: Pair<String, String>? = null
        show(inputApproval(prompt)) { _, decision, input -> submitted = decision to input }
        compose.onNode(hasSetTextAction()).performTextInput("保留完整问题和输入内容")
        compose.onNodeWithContentDescription("收起").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("展开").performClick()

        compose.onNodeWithText("保留完整问题和输入内容").assertExists()
        compose.onNodeWithText("提交").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("submit" to "保留完整问题和输入内容", submitted) }
    }

    @Test fun ordinaryApprovalKeepsItsCompactSummaryAndDecision() {
        val summary = "这是普通工具的审批摘要，保持原有的紧凑显示，不因为 Ask 正文的修改而展开。".repeat(3)
        val approval = PendingApproval(
            provider = "test",
            id = "permission",
            method = "permission",
            summary = summary,
            options = listOf(ApprovalOption("allow", "允许", "allow")),
        )
        var decision: String? = null
        show(approval) { _, selected, _ -> decision = selected }

        assertEquals(1, textLayout(summary).lineCount)
        compose.onNodeWithContentDescription("展开").assertIsDisplayed()
        compose.onNodeWithText("允许").performClick()
        compose.runOnIdle { assertEquals("allow", decision) }
    }

    @Test fun credentialInputRemainsMasked() {
        show(inputApproval("请填写访问凭据。").copy(method = "credential"))

        compose.onNode(hasSetTextAction()).performTextInput("example-secret")
        compose.onNode(hasSetTextAction())
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("明文不会发给模型，也不会出现在聊天记录里。").assertExists()
    }

    private fun inputApproval(prompt: String) = PendingApproval(
        provider = "test",
        id = "ask",
        method = "input",
        reason = prompt,
        options = listOf(
            ApprovalOption("submit", "提交", "submit"),
            ApprovalOption("cancel", "取消", "cancel"),
        ),
    )

    private fun show(
        approval: PendingApproval,
        fontScale: Float = 1f,
        onDecision: (PendingApproval, String, String) -> Unit = { _, _, _ -> },
    ) {
        compose.setContent {
            CloverTheme(fontScale = fontScale) {
                Surface {
                    Box(Modifier.width(240.dp)) {
                        ApprovalCards(listOf(approval), onDecision)
                    }
                }
            }
        }
    }

    private fun textLayout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}
