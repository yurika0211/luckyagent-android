package com.luckyagent.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timeline
import androidx.activity.compose.BackHandler
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.luckyagent.android.ui.screens.RuntimeConfigScreen
import com.luckyagent.android.ui.screens.SettingsScreen
import com.luckyagent.android.ui.screens.decodeQrUri
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
/** Minimum comfortable width reserved for primary chat/content when side panes are fixed. */
internal val MinMainContentWidth = 420.dp

private data class NavSpec(
    val dest: AppDestination,
    val label: String,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavSpec(AppDestination.Chat, "聊天", Icons.Outlined.ChatBubbleOutline),
    NavSpec(AppDestination.Tasks, "任务", Icons.Outlined.Assignment),
    NavSpec(AppDestination.Background, "后台任务", Icons.Outlined.Autorenew),
    NavSpec(AppDestination.Cron, "定时任务 Cron", Icons.Outlined.Schedule),
    NavSpec(AppDestination.Trajectory, "执行轨迹", Icons.Outlined.Timeline),
    NavSpec(AppDestination.Skills, "技能", Icons.Outlined.Extension),
    NavSpec(AppDestination.Memory, "记忆", Icons.Outlined.AccountTree),
    NavSpec(AppDestination.Gateways, "网关", Icons.Outlined.Hub),
    NavSpec(AppDestination.RuntimeConfig, "config.json", Icons.Outlined.Code),
    NavSpec(AppDestination.Settings, "基础设置", Icons.Outlined.Settings),
)

private fun nav(dest: AppDestination) = navItems.first { it.dest == dest }

/** Chat and Settings are pinned top/bottom; the rest are grouped by what the user is checking. */
private val navGroups = listOf(
    "运行与自动化" to listOf(AppDestination.Tasks, AppDestination.Background, AppDestination.Cron, AppDestination.Trajectory).map(::nav),
    "资源与连接" to listOf(AppDestination.Skills, AppDestination.Memory, AppDestination.Gateways, AppDestination.RuntimeConfig).map(::nav),
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
    val navigationPager = rememberPagerState(initialPage = 1, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val openNavigation: () -> Unit = remember(navigationPager, scope) {
        { scope.launch { navigationPager.animateScrollToPage(0) }; Unit }
    }
    val navigationOpen = state.destination != AppDestination.Chat && navigationPager.currentPage == 0
    BackHandler(enabled = navigationOpen) {
        scope.launch { navigationPager.animateScrollToPage(1) }
    }
    BackHandler(
        enabled = !navigationOpen && (
            state.destination != AppDestination.Chat ||
                state.selectedTaskId != null ||
                state.selectedBackgroundTaskId != null
            ),
    ) {
        vm.handleSystemBack()
    }

    // Chat owns a continuous navigation/chat/sessions pager on compact layouts.
    // Other destinations reuse the same full-page navigation beside their content.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp && maxWidth >= (AppNavRailWidth + MinMainContentWidth)

        if (useRail) {
            // NavigationRail is always visible on large layouts.
            AppScaffold(
                state = state,
                vm = vm,
                useRail = true,
                openNavigation = null,
            )
        } else if (state.destination == AppDestination.Chat) {
            AppScaffold(
                state = state,
                vm = vm,
                useRail = false,
                openNavigation = openNavigation,
                navigationContent = { onSelect, onClose ->
                    NavigationPage(state = state, onSelect = onSelect, onClose = onClose)
                },
            )
        } else {
            val focusManager = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(navigationPager.settledPage, state.destination) {
                focusManager.clearFocus()
                keyboard?.hide()
            }
            HorizontalPager(
                state = navigationPager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                // Feature pages own their drag gestures (blackboard, memory graph,
                // horizontal lists). Open navigation with the header button; its
                // full-page menu still supports swiping back to the feature page.
                userScrollEnabled = navigationPager.settledPage == 0,
            ) {
                if (it == 0) {
                    NavigationPage(
                        state = state,
                        onSelect = { destination ->
                            scope.launch {
                                navigationPager.scrollToPage(1)
                                vm.navigate(destination)
                            }
                        },
                        onClose = { scope.launch { navigationPager.animateScrollToPage(1) } },
                    )
                } else {
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
    openNavigation: (() -> Unit)?,
    navigationContent: (@Composable (onSelect: (AppDestination) -> Unit, onClose: () -> Unit) -> Unit)? = null,
) {
    val context = LocalContext.current
    var scanningPairing by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> scanningPairing = granted }
    val qrImagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { decodeQrUri(context, uri) }.getOrNull()
        if (text.isNullOrBlank()) vm.reportPairingScanFailed()
        else vm.applyPairingQr(text)
    }
    val startPairingScan = {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) scanningPairing = true
        else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }
    val surfaceOpacity = com.luckyagent.android.ui.components.SurfaceOpacity(
        chrome = state.settings.chromeOpacity.coerceIn(0, 100) / 100f,
        card = state.settings.cardOpacity.coerceIn(0, 100) / 100f,
        drawer = state.settings.drawerOpacity.coerceIn(0, 100) / 100f,
    )
    CompositionLocalProvider(
        LocalOpenNavigationDrawer provides openNavigation,
        com.luckyagent.android.ui.components.LocalSurfaceOpacity provides surfaceOpacity,
    ) {
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
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = state.settings.drawerOpacity.coerceIn(0, 100) / 100f),
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
                            if (item.dest == AppDestination.Settings) Spacer(Modifier.height(12.dp))
                            NavigationRailItem(
                                selected = state.destination == item.dest,
                                onClick = { vm.navigate(item.dest) },
                                icon = {
                                    val badge = badgeColor(item.dest, state)
                                    if (badge == null) Icon(item.icon, contentDescription = item.label)
                                    else androidx.compose.material3.BadgedBox(badge = { androidx.compose.material3.Badge(containerColor = badge) }) {
                                        Icon(item.icon, contentDescription = item.label)
                                    }
                                },
                                label = { Text(if (item.dest == AppDestination.Cron) "Cron" else item.label, maxLines = 1) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                    Box(Modifier.widthIn(max = 1440.dp).fillMaxSize()) {
                        when (state.destination) {
                            AppDestination.Chat -> ChatScreen(
                                state = state,
                                vm = vm,
                                railOccupied = useRail,
                                onOpenSettings = { vm.navigate(AppDestination.Settings) },
                                navigationContent = navigationContent,
                            )
                            AppDestination.Tasks -> TasksScreen(state = state, vm = vm)
                            AppDestination.Background -> BackgroundScreen(state = state, vm = vm)
                            AppDestination.Cron -> CronScreen(state = state, vm = vm)
                            AppDestination.Trajectory -> TrajectoryScreen(state = state, vm = vm)
                            AppDestination.Gateways -> GatewaysScreen(state = state, vm = vm)
                            AppDestination.Skills -> SkillsScreen(state = state, vm = vm)
                            AppDestination.Memory -> MemoryScreen(state = state, vm = vm)
                            AppDestination.RuntimeConfig -> RuntimeConfigScreen(state = state, vm = vm)
                            AppDestination.Settings -> SettingsScreen(
                                state = state,
                                vm = vm,
                                onBack = { vm.navigate(AppDestination.Chat) },
                                onScan = startPairingScan,
                                onPickQrImage = {
                                    qrImagePicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
            if (scanningPairing) {
                com.luckyagent.android.ui.screens.PairingScannerDialog(
                    onDismiss = { scanningPairing = false },
                    onScanned = { text ->
                        scanningPairing = false
                        vm.applyPairingQr(text)
                    },
                )
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
private fun NavigationPage(
    state: AppUiState,
    onSelect: (AppDestination) -> Unit,
    onClose: () -> Unit,
) {
    val live = com.luckyagent.android.ui.components.socketStateLive(state.socketState)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Keep the brand quiet and useful: the connection state sits in its own
        // compact surface so it reads as status instead of competing with nav.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
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
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("LuckyAgent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "本地客户端",
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭功能导航")
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .48f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (live) CloverLeaf else CloverError),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.settings.apiBase.isBlank()) "未设置服务地址"
                        else com.luckyagent.android.ui.components.socketStateLabel(state.socketState),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (state.settings.apiBase.isNotBlank()) {
                        Text(
                            state.settings.apiBase,
                            style = MaterialTheme.typography.labelSmall,
                            color = CloverText3,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            DrawerNavItem(nav(AppDestination.Chat), state, onSelect)
            navGroups.forEach { (groupLabel, groupItems) ->
                Text(
                    groupLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
                )
                groupItems.forEach { DrawerNavItem(it, state, onSelect) }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            DrawerNavItem(nav(AppDestination.Settings), state, onSelect)
        }
    }
}

@Composable
private fun DrawerNavItem(
    item: NavSpec,
    state: AppUiState,
    onSelect: (AppDestination) -> Unit,
) {
    val badge = badgeColor(item.dest, state)
    NavigationDrawerItem(
        label = { Text(item.label) },
        icon = {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (state.destination == item.dest) CloverAccent.copy(alpha = .12f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, contentDescription = null)
            }
        },
        badge = badge?.let { color -> { Box(Modifier.size(8.dp).clip(CircleShape).background(color)) } },
        selected = state.destination == item.dest,
        onClick = { onSelect(item.dest) },
        shape = RoundedCornerShape(14.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = CloverAccent.copy(alpha = .10f),
            unselectedContainerColor = Color.Transparent,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = CloverText2,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 2.dp),
    )
}
