package com.luckyagent.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
    NavSpec(AppDestination.Trajectory, "Trace", Icons.Outlined.Timeline),
    NavSpec(AppDestination.Gateways, "Gateways", Icons.Outlined.Hub),
    NavSpec(AppDestination.Skills, "Skills", Icons.Outlined.Extension),
    NavSpec(AppDestination.Memory, "Memory", Icons.Outlined.AccountTree),
    NavSpec(AppDestination.Settings, "Settings", Icons.Outlined.Settings),
)

private val primaryNavItems = navItems.filterNot { it.dest == AppDestination.Gateways || it.dest == AppDestination.Skills }
private val secondaryNavItems = navItems.filter { it.dest == AppDestination.Gateways || it.dest == AppDestination.Skills }

@Composable
fun LuckyAgentAppRoot(vm: AppViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val widthDp = LocalConfiguration.current.screenWidthDp
    val useRail = widthDp >= 700
    var showMore by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(CloverBg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        bottomBar = {
            if (!useRail) {
                NavigationBar(containerColor = CloverBgSide) {
                    primaryNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = state.destination == item.dest,
                            onClick = { vm.navigate(item.dest) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                    NavigationBarItem(
                        selected = state.destination == AppDestination.Gateways || state.destination == AppDestination.Skills,
                        onClick = { showMore = true },
                        icon = { Icon(Icons.Outlined.MoreHoriz, contentDescription = "More sections") },
                        label = { Text("More", style = MaterialTheme.typography.labelSmall) },
                    )
                    if (showMore) {
                        AlertDialog(
                            onDismissRequest = { showMore = false },
                            title = { Text("More sections") },
                            text = {
                                Column {
                                    secondaryNavItems.forEach { item ->
                                        TextButton(
                                            onClick = {
                                                showMore = false
                                                vm.navigate(item.dest)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(item.icon, contentDescription = null)
                                            Text(item.label, modifier = Modifier.padding(start = 12.dp))
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showMore = false }) { Text("Close") }
                            },
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 1440.dp).fillMaxSize()) {
                    when (state.destination) {
                        AppDestination.Chat -> ChatScreen(state = state, vm = vm)
                        AppDestination.Trajectory -> TrajectoryScreen(state = state, vm = vm)
                        AppDestination.Gateways -> GatewaysScreen(state = state, vm = vm)
                        AppDestination.Skills -> SkillsScreen(state = state, vm = vm)
                        AppDestination.Memory -> MemoryScreen(state = state, vm = vm)
                        AppDestination.Settings -> SettingsScreen(state = state, vm = vm)
                    }
                }
            }
        }
    }
}
