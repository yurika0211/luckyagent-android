package com.luckyagent.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luckyagent.android.ui.screens.ChatScreen
import com.luckyagent.android.ui.screens.GatewaysScreen
import com.luckyagent.android.ui.screens.MemoryScreen
import com.luckyagent.android.ui.screens.SettingsScreen
import com.luckyagent.android.ui.screens.SkillsScreen
import com.luckyagent.android.ui.screens.TrajectoryScreen
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverBgSide

private data class NavSpec(
    val dest: AppDestination,
    val label: String,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavSpec(AppDestination.Chat, "Chat", Icons.Outlined.ChatBubbleOutline),
    NavSpec(AppDestination.Trajectory, "Trajectory", Icons.Outlined.Timeline),
    NavSpec(AppDestination.Gateways, "Gateways", Icons.Outlined.Hub),
    NavSpec(AppDestination.Skills, "Skills", Icons.Outlined.Extension),
    NavSpec(AppDestination.Memory, "Memory", Icons.Outlined.AccountTree),
    NavSpec(AppDestination.Settings, "Settings", Icons.Outlined.Settings),
)

@Composable
fun LuckyAgentAppRoot(vm: AppViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val widthDp = LocalConfiguration.current.screenWidthDp
    val useRail = widthDp >= 700

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(CloverBg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        bottomBar = {
            if (!useRail) {
                NavigationBar(containerColor = CloverBgSide) {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            selected = state.destination == item.dest,
                            onClick = { vm.navigate(item.dest) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (useRail) {
                NavigationRail(containerColor = CloverBgSide, modifier = Modifier.width(88.dp)) {
                    navItems.forEach { item ->
                        NavigationRailItem(
                            selected = state.destination == item.dest,
                            onClick = { vm.navigate(item.dest) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                when (state.destination) {
                    AppDestination.Chat -> ChatScreen(state = state, vm = vm)
                    AppDestination.Trajectory -> TrajectoryScreen(state = state, onRefresh = vm::refreshTrajectory)
                    AppDestination.Gateways -> GatewaysScreen(state = state, onRefresh = vm::refreshGateways)
                    AppDestination.Skills -> SkillsScreen(state = state, onRefresh = vm::refreshSkills)
                    AppDestination.Memory -> MemoryScreen(state = state, onRefresh = vm::refreshMemory)
                    AppDestination.Settings -> SettingsScreen(state = state, vm = vm)
                }
            }
        }
    }
}
