package app.seb3thehacker.gearslip.car

import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.car.app.model.CarIcon
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.seb3thehacker.gearslip.R
import app.seb3thehacker.gearslip.call.CarCalls
import app.seb3thehacker.gearslip.call.CallUiState
import app.seb3thehacker.gearslip.car.theme.GsColors
import app.seb3thehacker.gearslip.car.theme.GsIconBox
import app.seb3thehacker.gearslip.car.theme.GsIconButton
import app.seb3thehacker.gearslip.car.theme.GsTone
import app.seb3thehacker.gearslip.car.theme.gsColors
import app.seb3thehacker.gearslip.host.CarAppConnection
import app.seb3thehacker.gearslip.host.display
import app.seb3thehacker.gearslip.host.text
import app.seb3thehacker.gearslip.media.CarMedia
import app.seb3thehacker.gearslip.media.MediaArt
import app.seb3thehacker.gearslip.notify.CarNotifications
import app.seb3thehacker.gearslip.notify.PhoneNavigation
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private enum class NetworkKind { WIFI, MOBILE_DATA, OFFLINE }

private fun currentNetworkKind(context: Context): NetworkKind {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return NetworkKind.OFFLINE
    val active = cm.activeNetwork ?: return NetworkKind.OFFLINE
    val caps = cm.getNetworkCapabilities(active) ?: return NetworkKind.OFFLINE
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.MOBILE_DATA
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkKind.MOBILE_DATA
        else -> NetworkKind.OFFLINE
    }
}

private fun openMobileDataOrNetworkSettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
        Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (ok) return
    }
}

@Composable
private fun BreadcrumbBar(navigator: CarNavigator) {
    val trail = navigator.trail
    if (trail.size <= 1) return
    val darkOutside = LocalDarkOutside.current
    val fg = if (darkOutside) Color.White else Color(0xFF141722)
    Row(
        Modifier.fillMaxWidth().height(38.dp).padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GsIconButton(
            Icons.AutoMirrored.Filled.ArrowBack,
            stringResource(R.string.back),
            navigator::back,
            size = 32.dp,
            iconSize = 20.dp,
        )
        Spacer(Modifier.width(4.dp))
        trail.forEachIndexed { index, crumb ->
            val isLast = index == trail.lastIndex
            Text(
                crumb.shown(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isLast) fg else fg.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isLast) Modifier else Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { navigator.jumpTo(index) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
            if (!isLast) {
                Text(
                    "›",
                    style = MaterialTheme.typography.labelLarge,
                    color = fg.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/**
 * Left-side CarPlay-style vertical frosted glass sidebar: clock & battery/weather at top,
 * default + user-pinned app shortcuts in the middle, and network/data + Settings/Apps/Home at bottom.
 */
@Composable
internal fun CarNavBar(navigator: CarNavigator, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val darkOutside = LocalDarkOutside.current
    val sidebarBg = if (darkOutside) Color(0xCC151822) else Color.White.copy(alpha = 0.68f)
    val sidebarFg = if (darkOutside) Color.White else Color(0xFF141722)
    val sidebarBorder = if (darkOutside) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.45f)

    val battery by CarEnvironment.battery.collectAsState()
    val now by rememberNow()
    val weather by Weather.state.collectAsState()
    val weatherData = (weather as? WeatherState.Ready)?.data

    val pinnedIds by CarSettings.pinnedApps.collectAsState()
    val entries = rememberAllEntries()
    val defaultPins = remember(entries) {
        buildList {
            entries.firstOrNull { it.template?.isNavigation == true }?.let(::add)
            entries.firstOrNull { it.media != null }?.let(::add)
            entries.firstOrNull { it.builtIn === BuiltInApps.Web || it.builtIn === BuiltInApps.Phone }?.let(::add)
        }
    }
    val pinnedEntries = remember(entries, pinnedIds, defaultPins) {
        if (pinnedIds.isNotEmpty()) {
            pinnedIds.mapNotNull { id -> entries.find { it.componentId == id } }
        } else {
            defaultPins
        }
    }
    val frame by CarEnvironment.frame.collectAsState()
    val running = rememberRunningApps()
    val navMic by CarSettings.navMic.collectAsState()
    val unread by CarNotifications.unread.collectAsState()

    val effectivePinIds = remember(pinnedEntries) { pinnedEntries.mapNotNull { it.componentId }.toSet() }
    val runningEntries = running.keys
        .filter { it !in effectivePinIds && running[it] != RunningSlot.NAV }
        .mapNotNull { id -> entries.find { it.componentId == id } }
    val shownPins = pinnedEntries.filter { running[it.componentId] != RunningSlot.NAV }
    val dockApps = (shownPins + runningEntries).take(3)

    Surface(
        modifier = modifier
            .width(CARPLAY_SIDEBAR_WIDTH)
            .fillMaxHeight()
            .border(width = 1.dp, color = sidebarBorder),
        color = sidebarBg,
        contentColor = sidebarFg,
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top: Time + Battery / Weather status
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { navigator.dashboard() }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (unread > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF3B30)),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)).replace(" AM", "").replace(" PM", ""),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = sidebarFg,
                        maxLines = 1,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (battery.percent >= 0) {
                            Text(
                                "${battery.percent}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = sidebarFg.copy(alpha = 0.78f),
                                maxLines = 1,
                            )
                            if (battery.charging) {
                                ChargingGlyph(Modifier.size(11.dp), tint = Color(0xFF1E9E47))
                            }
                        }
                        weatherData?.let { w ->
                            Text(
                                "${w.temp}°",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = sidebarFg.copy(alpha = 0.78f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // Middle: Up to 3 pinned / recent app shortcuts (CarPlay dock style)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                dockApps.forEach { entry ->
                    NavEntryIcon(entry, running, navigator, frame)
                }
            }

            // Bottom: Network/Data status + Settings + Apps + Home
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NavNetworkDataIcon(context)
                if (navMic) {
                    NavAssistantIcon()
                }
                NavItem(
                    icon = Icons.Filled.Settings,
                    selected = navigator.current == CarScreen.Settings,
                    onClick = {
                        if (navigator.current == CarScreen.Settings) navigator.home()
                        else navigator.settings()
                    },
                )
                NavItem(
                    icon = MediaIcons.Apps,
                    selected = navigator.current == CarScreen.Apps,
                    onClick = {
                        if (navigator.current == CarScreen.Apps) navigator.home()
                        else navigator.apps()
                    },
                )
                NavHomeButton(navigator, entries, running)
            }
        }
    }
}

@Composable
private fun NavNetworkDataIcon(context: Context) {
    val netKind by produceState(initialValue = currentNetworkKind(context), context) {
        while (true) {
            value = currentNetworkKind(context)
            delay(2_000L)
        }
    }
    val (bgColor, iconTint, badgeText, glyph) = when (netKind) {
        NetworkKind.WIFI -> Quad(Color(0xFF0091EA).copy(alpha = 0.22f), Color(0xFF0077C2), "WiFi", Icons.Filled.Share)
        NetworkKind.MOBILE_DATA -> Quad(Color(0xFF00C853).copy(alpha = 0.22f), Color(0xFF008E3B), "Data", Icons.Filled.Refresh)
        NetworkKind.OFFLINE -> Quad(Color(0xFFFF3B30).copy(alpha = 0.20f), Color(0xFFD32F2F), "Off", Icons.Filled.Refresh)
    }

    GsIconBox(
        onClick = { openMobileDataOrNetworkSettings(context) },
        modifier = Modifier.size(46.dp),
        colors = GsColors(bgColor, iconTint),
        shape = RoundedCornerShape(13.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = glyph,
                contentDescription = "Network & Data",
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                badgeText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = iconTint,
                maxLines = 1,
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private val CARPLAY_SIDEBAR_WIDTH = 84.dp
private val NAV_ITEM_PADDING = 7.dp
private const val TITLE_SHOWN_MS = 3_000L
private val TURN_AND_PLAYER_MIN_WIDTH = 520.dp

@Composable
private fun NavHomeButton(navigator: CarNavigator, entries: List<Entry>, running: Map<String, RunningSlot>) {
    val selected = navigator.current == CarScreen.Home
    val navId = running.entries.firstOrNull { it.value == RunningSlot.NAV }?.key
    val navEntry = navId?.let { id -> entries.find { it.componentId == id } }
    GsIconBox(
        { navigator.home() },
        modifier = Modifier.size(48.dp),
        colors = navColors(selected),
        latched = selected,
        shape = RoundedCornerShape(13.dp),
        onLongClick = navId?.let { id -> { navigator.showAppMenu(id) } },
    ) {
        val icon = navEntry?.icon
        if (icon != null) {
            Image(icon, navEntry.label, Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)))
        } else {
            Icon(
                ImageVector.vectorResource(R.drawable.navigation_24),
                contentDescription = stringResource(R.string.crumb_home),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun NavEntryIcon(
    entry: Entry,
    running: Map<String, RunningSlot>,
    navigator: CarNavigator,
    frame: CarEnvironment.Frame,
) {
    val id = entry.componentId ?: return
    val slot = running[id]
    NavAppIcon(
        entry,
        open = slot != null,
        showing = slot != null && slot.isShowing(navigator.current),
        onLongClick = { navigator.showAppMenu(id) },
    ) { openApp(entry, slot, navigator, frame) }
}

@Composable
private fun NavAppIcon(
    entry: Entry,
    open: Boolean,
    showing: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    val darkOutside = LocalDarkOutside.current
    val baseBg = if (darkOutside) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.45f)
    val activeBg = if (darkOutside) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.85f)
    val fg = if (darkOutside) Color.White else Color(0xFF141722)
    val highlight = if (showing) activeBg else baseBg
    GsIconBox(
        onClick,
        Modifier.size(48.dp),
        colors = GsColors(highlight, fg),
        latched = showing,
        shape = RoundedCornerShape(13.dp),
        onLongClick = onLongClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            entry.icon?.let {
                Image(it, entry.label, Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)))
            }
            entry.builtIn?.let {
                ColorfulBuiltInIcon(
                    idOrLabel = it.id.ifBlank { entry.label },
                    icon = it.glyph,
                    contentDescription = entry.shownLabel(),
                    boxSize = 34.dp,
                    iconSize = 20.dp,
                    cornerRadius = 9.dp,
                )
            }
            if (open) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp)
                        .size(width = 14.dp, height = 3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF007AFF)),
                )
            }
        }
    }
}

@Composable
private fun navColors(selected: Boolean): GsColors {
    val darkOutside = LocalDarkOutside.current
    val unselectedBg = if (darkOutside) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.45f)
    val unselectedFg = if (darkOutside) Color.White else Color(0xFF141722)
    return if (selected) GsColors(Color(0xFF007AFF), Color.White)
    else GsColors(unselectedBg, unselectedFg)
}

@Composable
private fun NavAssistantIcon() {
    val state by CarAssistant.state.collectAsState()
    val active = state.phase != CarAssistant.Phase.IDLE
    GsIconBox(
        { CarAssistant.start() },
        modifier = Modifier.size(46.dp),
        colors = navColors(active),
        latched = active,
        shape = RoundedCornerShape(13.dp),
    ) {
        MicGlyph(Modifier.padding(NAV_ITEM_PADDING).size(24.dp))
    }
}

@Composable
private fun NavItem(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    GsIconBox(
        onClick,
        modifier = Modifier.size(46.dp),
        colors = navColors(selected),
        latched = selected,
        shape = RoundedCornerShape(13.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(NAV_ITEM_PADDING).size(24.dp))
    }
}

@Composable
internal fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(60_000 - value % 60_000)
    }
}

private class NextTurn(val icon: CarIcon?, val distance: String, val road: String, val fromPhone: Boolean = false)

@Composable
private fun rememberNextTurn(): NextTurn? {
    val status by CarServices.nav.status.collectAsState()
    val template by CarServices.nav.template.collectAsState()
    CarSettings.units.collectAsState().value
    val phone by PhoneNavigation.turn.collectAsState()
    val routing = ((template as? NavigationTemplate)?.navigationInfo as? RoutingInfo)
        ?.takeIf { status.phase == CarAppConnection.Phase.RUNNING }
    val step = routing?.takeIf { !it.isLoading }?.currentStep ?: return phone?.let { phoneTurn(it) }
    return NextTurn(
        icon = step.maneuver?.icon,
        distance = routing.currentDistance.display(),
        road = step.road.text().ifEmpty { step.cue.text() },
    )
}

@Composable
private fun phoneTurn(turn: PhoneNavigation.Turn): NextTurn {
    val icon = remember(turn.arrow) {
        turn.arrow?.let { CarIcon.Builder(androidx.core.graphics.drawable.IconCompat.createWithBitmap(it)).build() }
    }
    return NextTurn(icon, turn.distance?.display() ?: turn.distanceText, turn.road, fromPhone = true)
}

@Composable
private fun NavTurnPill(turn: NextTurn, onClick: () -> Unit) {
    GsIconBox(
        onClick,
        Modifier.widthIn(max = 340.dp).height(44.dp),
        colors = gsColors(GsTone.Primary),
    ) {
        Row(
            Modifier.padding(NAV_ITEM_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CarGlyph(turn.icon, Modifier.size(28.dp))
            if (turn.distance.isNotEmpty()) {
                Text(turn.distance, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (turn.road.isNotEmpty()) {
                Text(
                    turn.road,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NavCallPill(call: CallUiState.Active, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val muted by CarCalls.muted.collectAsState()
    val elapsed by produceState(0L, call.since) {
        while (true) {
            value = (android.os.SystemClock.uptimeMillis() - call.since) / 1000
            delay(1000 - (android.os.SystemClock.uptimeMillis() - call.since) % 1000)
        }
    }
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = scheme.secondaryContainer,
        contentColor = scheme.on