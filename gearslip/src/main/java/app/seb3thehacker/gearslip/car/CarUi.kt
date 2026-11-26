package app.seb3thehacker.gearslip.car

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.seb3thehacker.gearslip.R
import app.seb3thehacker.gearslip.call.CallUiState
import app.seb3thehacker.gearslip.call.CarCalls
import app.seb3thehacker.gearslip.car.theme.GsButton
import app.seb3thehacker.gearslip.car.theme.GsThemes
import app.seb3thehacker.gearslip.car.theme.GsTone
import app.seb3thehacker.gearslip.car.theme.LocalGsTheme
import app.seb3thehacker.gearslip.car.theme.withTintedSurfaces
import app.seb3thehacker.gearslip.host.CarAppConnection
import kotlinx.coroutines.delay

/**
 * The whole car screen: starts on the CarPlay-style Split Home Screen, shows the left frosted
 * taskbar on Home & Apps, and auto-hides the taskbar smoothly when any app is opened.
 */
@Composable
fun CarUi() {
    val scale by CarSettings.scale.collectAsState()
    val phoneDark by CarEnvironment.phoneDark.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val appTheme by CarSettings.appTheme.collectAsState()
    val nightMode by CarSettings.nightMode.collectAsState()
    val uiThemeId by CarSettings.uiTheme.collectAsState()
    val darkStyle by CarSettings.darkStyle.collectAsState()
    val mediaApp by CarServices.mediaApp.collectAsState()
    val darkOutside by CarEnvironment.darkOutside.collectAsState()
    val appContext = LocalContext.current.applicationContext

    remember(appContext) {
        CarSettings.init(appContext)
        Prefetch.warm(appContext, force = true)
        CarServices.init(appContext)
    }

    DisposableEffect(Unit) {
        CarSettings.onThemeChanged = { runCatching { CarEnvironment.refresh() } }
        onDispose {
            CarSettings.onThemeChanged = null
            CarServices.shutdown()
        }
    }

    val dark = when (appTheme) {
        AppTheme.PHONE -> phoneDark || systemDark
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    val effectiveDarkOutside = dark || darkOutside || nightMode == NightMode.NIGHT
    val colors = (if (dark) dynamicDarkColorScheme(appContext) else dynamicLightColorScheme(appContext))
        .withTintedSurfaces(dark, darkStyle)
    val base = LocalDensity.current
    val insets by CarEnvironment.insets.collectAsState()
    // Always boot directly into the CarPlay Split Home Screen first
    val navigator = remember { CarNavigator(CarSettings.HOME) }
    val controlsWake = remember { MapControlsWake() }
    LaunchedEffect(controlsWake) {
        while (true) {
            delay(250)
            controlsWake.tick()
            CarFullscreen.tick()
        }
    }
    LaunchedEffect(controlsWake) { CarKeys.focusMoves.collect { controlsWake.touch() } }
    LaunchedEffect(Unit) { if (CarSettings.pipesNow) app.seb3thehacker.gearslip.audio.CarAudio.request() }
    LaunchedEffect(Unit) { app.seb3thehacker.gearslip.update.UpdateChecker.onCarConnected(appContext) }
    LaunchedEffect(effectiveDarkOutside) { CarServices.nav.pushConfiguration() }

    val frameForAssistant by CarEnvironment.frame.collectAsState()
    LaunchedEffect(navigator) {
        CarAssistant.pendingCommand.collect { heard ->
            if (heard != null) {
                val reply = AssistantCommands.handle(appContext, navigator, frameForAssistant, heard)
                CarAssistant.reply(reply)
            }
        }
    }

    LaunchedEffect(navigator) {
        CarKeys.screenKeys.collect { key ->
            when (key) {
                CarKeys.ScreenKey.BACK -> when {
                    navigator.appMenu != null -> navigator.dismissAppMenu()
                    VoiceReply.state.value.phase != VoiceReply.Phase.IDLE -> VoiceReply.cancel()
                    else -> navigator.back()
                }
                CarKeys.ScreenKey.HOME -> navigator.home()
                CarKeys.ScreenKey.MAP -> navigator.home()
                CarKeys.ScreenKey.MEDIA ->
                    if (CarServices.mediaApp.value != null || CarServices.reopenLastMedia()) navigator.media()
                    else navigator.apps()
                CarKeys.ScreenKey.PHONE -> BuiltInApps.Phone.open(navigator)
            }
        }
    }

    val view = LocalView.current
    DisposableEffect(view) {
        view.isHapticFeedbackEnabled = false
        onDispose { }
    }

    CompositionLocalProvider(
        LocalHapticFeedback provides NoHaptics,
        LocalDensity provides Density(base.density * scale, base.fontScale),
        LocalCarNavigator provides navigator,
        LocalDarkOutside provides effectiveDarkOutside,
        LocalMapControlsWake provides controlsWake,
        LocalGsTheme provides GsThemes.byId(uiThemeId),
    ) {
        MaterialTheme(colorScheme = colors) {
          CompositionLocalProvider(LocalIndication provides FocusRingIndication(MaterialTheme.colorScheme.primary)) {
            Surface(
                Modifier
                    .fillMaxSize()
                    .pointerInput(controlsWake) {
                        val edge = 32.dp.toPx()
                        val slop = 24.dp.toPx()
                        var startX = -1f
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                controlsWake.touch()
                                CarFullscreen.touch()
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.pressed && !change.previousPressed) {
                                    val x = change.position.x
                                    startX = if (x < edge) x else -1f
                                } else if (startX >= 0f && change.pressed && (change.position.x - startX) > slop) {
                                    // Swiping from the left edge inside any open app returns smoothly to Home
                                    if (navigator.current != CarScreen.Home && navigator.current != CarScreen.Apps) {
                                        navigator.home()
                                    }
                                    CarFullscreen.reveal()
                                    startX = -1f
                                }
                            }
                        }
                    },
                color = MaterialTheme.colorScheme.background,
            ) {
                val pad = with(LocalDensity.current) {
                    PaddingValues(
                        start = insets.left.toDp(), top = insets.top.toDp(),
                        end = insets.right.toDp(), bottom = insets.bottom.toDp(),
                    )
                }
                val screen = navigator.current
                val safetyWarningSeen by CarSettings.hasSeenSafetyWarning.collectAsState()
                val keyboardVisible by CarKeyboardVisibility.state
                val onCall = CarCalls.state.collectAsState().value is CallUiState.Active

                // Show left taskbar on Home & Apps; auto-hide it when any app screen is open
                val isLauncherSurface = screen == CarScreen.Home || screen == CarScreen.Apps
                val barShown = !keyboardVisible && (isLauncherSurface || onCall)
                // Zero bottom inset so there is no white strip at the bottom
                val homeInset = 0.dp
                val fullscreen = CarFullscreen.active && screen != CarScreen.Home
                val floatingHomeVisible = !barShown && !keyboardVisible && (!fullscreen || CarFullscreen.revealed)

                Box(Modifier.fillMaxSize().padding(pad)) {
                Column(Modifier.fillMaxSize()) {
                    if (!safetyWarningSeen) {
                        CarSafetyWizard(onDone = CarSettings::markSafetyWarningSeen)
                        return@Column
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CompositionLocalProvider(LocalNavBarInset provides homeInset) { CarHome() }
                    }
                }

                val lastScreen = remember { arrayOf<CarScreen>(CarScreen.Home) }
                val fromHome = remember(screen) { lastScreen[0] == CarScreen.Home }
                SideEffect { lastScreen[0] = screen }
                val enter = remember(screen) { Animatable(0f) }
                LaunchedEffect(screen) { enter.animateTo(1f, tween(200)) }
                val lastAway = remember { arrayOf<CarScreen>(CarScreen.Home) }
                val shown = if (screen != CarScreen.Home) screen else lastAway[0]
                SideEffect { if (screen != CarScreen.Home) lastAway[0] = screen }

                if (safetyWarningSeen) {
                    Box(Modifier.fillMaxSize()) {
                        AnimatedVisibility(
                            visible = screen != CarScreen.Home,
                            enter = EnterTransition.None,
                            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 40 },
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { if (fromHome) alpha = enter.value }
                                    .background(MaterialTheme.colorScheme.background),
                            ) {
                                Box(
                                    Modifier.fillMaxSize().graphicsLayer {
                                        if (!fromHome) alpha = enter.value
                                        translationY = (1f - enter.value) * 14.dp.toPx()
                                    },
                                ) {
                                    when (shown) {
                                        CarScreen.Home -> Unit
                                        CarScreen.Apps -> CarLauncher()
                                        CarScreen.Media -> CompositionLocalProvider(
                                            LocalNavBarInset provides 0.dp,
                                        ) { mediaApp?.let { MediaScreen(it) { navigator.back() } } }
                                        CarScreen.Browse -> BrowseAppScreen(navigator)
                                        CarScreen.Settings -> CarSettingsScreen()
                                        CarScreen.Dashboard -> CarDashboardScreen()
                                        CarScreen.Weather -> WeatherScreen()
                                        CarScreen.VehicleData -> VehicleDataScreen()
                                        is CarScreen.Notifications -> NotificationsScreen(shown.replyTo)
                                        is CarScreen.Messages -> MessagesScreen(shown.packageName)
                                        is CarScreen.App -> CarApps.find(shown.id)?.content?.invoke()
                                    }
                                }
                            }
                        }
                        CarToastOverlay(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
                        CarAlertOverlay(Modifier.align(Alignment.TopCenter))
                        CallPromptOverlay()
                        BluetoothPairOverlay()
                        PopupTimer()
                        NotificationPopup(
                            showNew = screen !is CarScreen.Notifications && screen != CarScreen.Dashboard,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                        VoiceHelpOverlay(Modifier.align(Alignment.Center))
                        CallOverlay()
                        AssistantOverlay(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))

                        // Left frosted taskbar: slides in smoothly on Home/Apps, hides inside open apps
                        AnimatedVisibility(
                            visible = barShown,
                            modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(),
                            enter = slideInHorizontally(tween(220)) { -it },
                            exit = slideOutHorizontally(tween(200)) { -it },
                        ) {
                            CarNavBar(navigator)
                        }

                        // Subtle floating Home pill when inside an app; auto-hides during fullscreen video
                        AnimatedVisibility(
                            visible = floatingHomeVisible,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp),
                            enter = slideInHorizontally(tween(200)) { -it },
                            exit = slideOutHorizontally(tween(180)) { -it },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.72f))
                                    .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                    .clickable { navigator.home() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.navigation_24),
                                    contentDescription = stringResource(R.string.crumb_home),
                                    tint = Color(0xFF141722),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        if (fullscreen) {
                            AnimatedVisibility(
                                CarFullscreen.revealed,
                                Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                                enter = slideInVertically { -it },
                                exit = slideOutVertically { -it },
                            ) {
                                GsButton(onClick = CarFullscreen::exit, modifier = Modifier.height(48.dp)) {
                                    Icon(MediaIcons.FullscreenExit, contentDescription = null, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.carui_exit_fullscreen), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
                AppMenu(navigator, rememberRunningApps())
                if (safetyWarningSeen) CarWhatsNew()
                }
            }
          }
        }
    }
}

internal val NAV_BAR_HEIGHT = 0.dp

internal val LocalNavBarInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

@Composable
private fun CarSafetyWizard(onDone: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.carui_drive_safely), style = ChromeType.headline, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.carui_unofficial),
                        style = ChromeType.body,
                    )
                    Text(
                        stringResource(R.string.carui_never_interact),
                        style = ChromeType.body,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            GsButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.carui_understand), style = ChromeType.body)
            }
        }
    }
}

@Composable
private fun BrowseAppScreen(navigator: CarNavigator) {
    val status by CarServices.browse.status.collectAsState()
    val frame by CarEnvironment.frame.collectAsState()
    val template by CarServices.browse.template.collectAsState()
    val leave = {
        CarServices.stopBrowse()
        navigator.back()
    }
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(template == null) {
        timedOut = false
        if (template == null) {
            delay(APP_START_TIMEOUT_MS)
            timedOut = true
        }
    }
    val failed = status.phase == CarAppConnection.Phase.REJECTED || status.phase == CarAppConnection.Phase.FAILED
    if (failed || timedOut) {
        AppWontStart(status.app ?: stringResource(R.string.carui_this_app), leave)
        return
    }
    CarAppStage(CarServices.browse, status, frame, onDisconnect = leave)
}

private const val APP_START_TIMEOUT_MS = 15_000L

@Composable
private fun AppWontStart(app: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.carui_app_unsupported, app), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.carui_app_unsupported_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GsButton(onClick = onBack, tone = GsTone.Tonal) { Text(stringResource(R.string.back)) }
    }
}

private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
}