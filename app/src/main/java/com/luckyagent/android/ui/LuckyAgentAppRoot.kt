package com.luckyagent.android.ui

import androidx.compose.foundation.background
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timeline
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luckyagent.android.ui.components.LocalOpenNavigationDrawer
import com.luckyagent.android.ui.screens.ChatScreen
import com.luckyagent.android.ui.screens.BackgroundScreen
import com.luckyagent.android.ui.screens.CronScreen
import com.luckyagent.android.ui.screens.GatewaysScreen
import com.luckyagent.android.ui.screens.MemoryScreen
import com.luckyagent.android.ui.screens.SettingsScreen
import com.luckyagent.android.ui.screens.SkillsScreen
import com.luckyagent.android.ui.screens.TasksScreen
import com.luckyagent.android.ui.screens.TrajectoryScreen
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverLeaf
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverText3
import com.luckyagent.android.ui.theme.CloverWarning
import kotlinx.coroutines.launch

internal val AppNavRailWidth = 88.dp
internal val AppNavDrawerWidth = 312.dp
/** Minimum comfortable width reserved for primary chat/content when side panes are fixed. */
internal val MinMainContentWidth = 420.dp

private data class NavSpec(
    val dest: AppDestination,
    val label: String,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavSpec(AppDestination.Chat, "Chat", Icons.Outlined.ChatBubbleOutline),
    NavSpec(AppDestination.Tasks, "Tasks", Icons.Outlined.Assignment),
    NavSpec(AppDestination.Background, "Background", Icons.Outlined.Assignment),
    NavSpec(AppDestination.Cron, "Cron", Icons.Outlined.Schedule),
    NavSpec(AppDestination.Trajectory, "Trace", Icons.Outlined.Timeline),
    NavSpec(AppDestination.Gateways, "Gateways", Icons.Outlined.Hub),
    NavSpec(AppDestination.Skills, "Skills", Icons.Outlined.Extension),
    NavSpec(AppDestination.Memory, "Memory", Icons.Outlined.AccountTree),
    NavSpec(AppDestination.Settings, "Settings", Icons.Outlined.Settings),
)

private val navGroups = listOf(
    "WORKSPACE" to navItems.take(1),
    "RUNTIME" to navItems.slice(1 until navItems.lastIndex),
    "SYSTEM" to navItems.takeLast(1),
)

/** Returns a badge color when a page has a notable status, or null when things look normal. */
private fun badgeColor(dest: AppDestination, state: AppUiState): Color? = when (dest) {
    AppDestination.Chat -> when {
        state.socketState == com.luckyagent.android.data.api.SocketState.Idle ||
            state.socketState == com.luckyagent.android.data.api.SocketState.Closed -> CloverError
        state.socketState == com.luckyagent.android.data.api.SocketState.Reconnecting -> CloverWarning
        else -> null
    }
    AppDestination.Gateways -> when {
        state.gatewaysError != null -> CloverError
        state.gateways.isNotEmpty() && state.gateways.none { it.running } -> CloverWarning
        else -> null
    }
    AppDestination.Tasks -> when {
        state.tasksError != null -> CloverError
        state.tasks.any { it.status.equals("failed", ignoreCase = true) } -> CloverWarning
        else -> null
    }
    AppDestination.Background -> when {
        state.backgroundError != null -> CloverError
        else -> null
    }
    AppDestination.Cron -> when {
        state.cronError != null -> CloverError
        else -> null
    }
    AppDestination.Settings -> when {
        state.healthOk == false -> CloverError
        else -> null
    }
    else -> null
}

@Composable
fun LuckyAgentAppRoot(vm: AppViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openNavigation: () -> Unit = remember(drawerState, scope) {
        { scope.launch { drawerState.open() }; Unit }
    }
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(
        enabled = !drawerState.isOpen && (
            state.destination != AppDestination.Chat ||
                state.selectedTaskId != null ||
                state.selectedBackgroundTaskId != null
            ),
    ) {
        vm.handleSystemBack()
    }

    // Decide rail vs modal drawer from available width, not a bare configuration dp.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp && maxWidth >= (AppNavRailWidth + MinMainContentWidth)

        if (useRail) {
            // NavigationRail is always visible; drawer open is a no-op on large layouts.
            AppScaffold(
                state = state,
                vm = vm,
                useRail = true,
                openNavigation = {},
            )
        } else {
            // Left-side app navigation (Material default / LTR). Do not force RTL just to flip the drawer.
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = false,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier
                            .widthIn(max = AppNavDrawerWidth)
                            .fillMaxHeight(),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .88f),
                    ) {
                        NavigationDrawerContent(
                            state = state,
                            onSelect = {
                                vm.navigate(it)
                                scope.launch { drawerState.close() }
                            },
                        )
                    }
                },
            ) {
                CompositionLocalProvider(LocalOpenNavigationDrawer provides openNavigation) {
                    AppScaffold(
                        state = state,
                        vm = vm,
                        useRail = false,
                        openNavigation = openNavigation,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppScaffold(
    state: AppUiState,
    vm: AppViewModel,
    useRail: Boolean,
    openNavigation: () -> Unit,
) {
    CompositionLocalProvider(LocalOpenNavigationDrawer provides openNavigation) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { _ ->
            Box(Modifier.fillMaxSize()) {
            WallpaperLayer(state.settings)
            Row(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier
                            .width(AppNavRailWidth)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .verticalScroll(rememberScrollState()),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .82f),
                    ) {
                        Spacer(Modifier.height(16.dp))
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(CloverAccent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "✣",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        navItems.forEach { item ->
                            NavigationRailItem(
                                selected = state.destination == item.dest,
                                onClick = { vm.navigate(item.dest) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                    Box(Modifier.widthIn(max = 1440.dp).fillMaxSize()) {
                        when (state.destination) {
                            AppDestination.Chat -> ChatScreen(state = state, vm = vm, railOccupied = useRail)
                            AppDestination.Tasks -> TasksScreen(state = state, vm = vm)
                            AppDestination.Background -> BackgroundScreen(state = state, vm = vm)
                            AppDestination.Cron -> CronScreen(state = state, vm = vm)
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
    }
}

@Composable
private fun WallpaperLayer(settings: com.luckyagent.android.data.settings.ClientSettings) {
    val context = LocalContext.current
    val file = settings.chatBackgroundFile.takeIf { it.isNotBlank() }?.let {
        com.luckyagent.android.data.settings.AppearanceStore.file(context, it)
    }?.takeIf { it.exists() }
    if (file == null) {
        Box(Modifier.fillMaxSize().background(com.luckyagent.android.ui.theme.CloverBg))
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(file)
            .memoryCacheKey(file.absolutePath + file.lastModified())
            .diskCacheKey(file.absolutePath + file.lastModified())
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = settings.chatBackgroundDim.coerceIn(0, 70) / 100f)))
}

@Composable
private fun NavigationDrawerContent(
    state: AppUiState,
    onSelect: (AppDestination) -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxHeight()
            .verticalScroll(scroll)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(CloverAccent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "✣",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("LuckyAgent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Mobile workspace", style = MaterialTheme.typography.bodySmall, color = CloverText3)
            }
            IconButton(onClick = { onSelect(AppDestination.Settings) }) {
                Icon(Icons.Outlined.ArrowForward, contentDescription = "Open settings")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.socketState == com.luckyagent.android.data.api.SocketState.Connected ||
                            state.socketState == com.luckyagent.android.data.api.SocketState.Running
                        ) {
                            CloverAccent
                        } else {
                            CloverText3
                        },
                    ),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Connection",
                    style = MaterialTheme.typography.labelMedium,
                    color = CloverText3,
                    modifier = Modifier.padding(start = 10.dp),
                )
                Text(
                    state.settings.apiBase.ifBlank { "Set runtime endpoint" },
                    style = MaterialTheme.typography.bodySmall,
                    color = CloverText2,
                    maxLines = 2,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(state.socketState.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = CloverAccent)
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        navGroups.forEach { (groupLabel, groupItems) ->
            Text(
                groupLabel,
                style = MaterialTheme.typography.labelSmall,
                color = CloverText3,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp),
            )
            groupItems.forEach { item ->
                val selected = state.destination == item.dest
                val badge = badgeColor(item.dest, state)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(if (selected) CloverLeaf.copy(alpha = .22f) else Color.Transparent)
                        .clickable { onSelect(item.dest) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary else CloverText2,
                    )
                    Text(
                        item.label,
                        modifier = Modifier.padding(start = 14.dp).weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (badge != null) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(badge),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "Profile & connection settings",
            style = MaterialTheme.typography.bodySmall,
            color = CloverText3,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}
