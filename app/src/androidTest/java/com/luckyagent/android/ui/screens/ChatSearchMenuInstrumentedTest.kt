package com.luckyagent.android.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.components.LocalOpenNavigationDrawer
import com.luckyagent.android.ui.theme.CloverTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatSearchMenuInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchOpensFromMoreAndReturnsToTheCompactTopBarAfterClosing() {
        var searching by mutableStateOf(false)
        var query by mutableStateOf("")
        val steps = mutableListOf<Int>()
        compose.setContent {
            CloverTheme {
                ChatTopBar(
                    state = AppUiState(),
                    onMenu = {},
                    showMenu = false,
                    onNewSession = {},
                    onOpenSettings = {},
                    onOpenModel = {},
                    onOpenCanvas = {},
                    onConnect = {},
                    searching = searching,
                    searchQuery = query,
                    onSearchQuery = { query = it },
                    onOpenSearch = { searching = true },
                    onCloseSearch = { searching = false },
                    onSearchStep = { steps += it },
                )
            }
        }

        compose.onNodeWithContentDescription("搜索此会话").assertDoesNotExist()
        compose.onNodeWithText("搜索").assertDoesNotExist()
        compose.onNodeWithContentDescription("新建会话").assertIsDisplayed()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("搜索").assertIsDisplayed().performClick()

        compose.onNodeWithText("搜索").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("needle")
        compose.onNodeWithText("上一条").performClick()
        compose.onNodeWithText("下一条").performClick()
        compose.runOnIdle {
            assertEquals("needle", query)
            assertEquals(listOf(-1, 1), steps)
        }
        compose.onNodeWithContentDescription("关闭搜索").performClick()

        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索此会话").assertDoesNotExist()
        compose.onNodeWithContentDescription("新建会话").assertIsDisplayed()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("搜索").assertIsDisplayed()
    }

    @Test fun addingSearchPreservesTheExistingMenuActions() {
        var selected = ""
        compose.setContent {
            CloverTheme {
                CompositionLocalProvider(LocalOpenNavigationDrawer provides { selected = "这台电脑" }) {
                    ChatTopBar(
                        state = AppUiState(),
                        onMenu = { selected = "会话列表" },
                        showMenu = false,
                        onNewSession = { selected = "新建会话" },
                        onOpenSettings = { selected = "设置" },
                        onOpenModel = {},
                        onOpenCanvas = { selected = "多 agent 画布" },
                        onConnect = {},
                        onReconnect = { selected = "重新连接" },
                    )
                }
            }
        }

        listOf("这台电脑", "会话列表", "设置", "重新连接", "多 agent 画布").forEach { label ->
            compose.onNodeWithContentDescription("更多").performClick()
            compose.onNodeWithText("搜索").assertIsDisplayed()
            compose.onNodeWithText(label).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(label, selected) }
            compose.onNodeWithText("搜索").assertDoesNotExist()
        }
        compose.onNodeWithContentDescription("新建会话").performClick()
        compose.runOnIdle { assertEquals("新建会话", selected) }
    }
}
