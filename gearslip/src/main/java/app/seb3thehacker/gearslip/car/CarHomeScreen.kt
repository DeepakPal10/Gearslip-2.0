package app.seb3thehacker.gearslip.car

import android.content.Context
import android.graphics.Bitmap
import android.media.session.PlaybackState
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seb3thehacker.gearslip.R
import app.seb3thehacker.gearslip.browser.SiteIcons
import app.seb3thehacker.gearslip.car.theme.GsButton
import app.seb3thehacker.gearslip.car.theme.GsIconButton
import app.seb3thehacker.gearslip.car.theme.GsTone
import app.seb3thehacker.gearslip.car.theme.gsPanelShadow
import app.seb3thehacker.gearslip.host.CarAppConnection
import app.seb3thehacker.gearslip.media.CarMedia
import app.seb3thehacker.gearslip.media.MediaArt
import app.seb3thehacker.gearslip.media.NowPlaying
import kotlinx.coroutines.launch

/**
 * Apple CarPlay-style Split Home Screen:
 * - Left sidebar inset (84.dp)
 * - Left primary rounded card: Live Navigation / Map widget
 * - Right column: Top quick-destination + pinned sites card + Bottom Now Playing Media widget
 */
@Composable
fun CarHome() {
    val frame by CarEnvironment.frame.collectAsStateWithLifecycle()
    val navStatus by CarServices.nav.status.collectAsStateWithLifecycle()
    val phase by CarServices.media.phase.collectAsStateWithLifecycle()
    val now by CarServices.media.now.collectAsStateWithLifecycle()
    val pinnedSites by PinnedWebSites.sites.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { CarServices.autostart(frame) }
    LaunchedEffect(phase) { if (phase == CarMedia.Phase.READY) CarServices.autoplayIfDue() }

    val navigator = LocalCarNavigator.current
    val panel = navigator.sidePanel

    val onHome = navigator.current == CarScreen.Home
    val seenHome = remember { booleanArrayOf(false) }
    LaunchedEffect(onHome) {
        if (onHome && seenHome[0]) CarServices.nav.replaceDeadSurface()
        if (onHome) seenHome[0] = true
    }

    Box(Modifier.fillMaxSize()) {
        // Bright blurred Apple wallpaper background
        CarPlayWallpaper()

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 96.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Left large Map Card (like CarPlay's map tile)
            Surface(
                modifier = Modifier
                    .weight(1.12f)
                    .fillMaxHeight()
                    .gsPanelShadow(CARD_SHAPE)
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CARD_SHAPE),
                shape = CARD_SHAPE,
                color = Color.White.copy(alpha = 0.76f),
                contentColor = Color(0xFF141722),
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalCardsAtEnd provides false,
                    LocalSidePanelInset provides 0.dp,
                ) {
                    MapPane(navStatus, frame)
                }
            }

            // Right stacked widgets: Top shortcuts/pinned sites + Bottom Media / Lyrics card
            Column(
                modifier = Modifier
                    .weight(0.88f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                QuickDestinationsCard(
                    frame = frame,
                    pinnedSites = pinnedSites,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.38f),
                )

                val mediaModifier = Modifier
                    .fillMaxWidth()
                    .weight(0.62f)

                if (panel == SidePanel.LYRICS && phase == CarMedia.Phase.READY && now.isActive) {
                    SideLyrics(now, mediaModifier)
                } else {
                    CarPlayMediaCard(now, mediaModifier)
                }
            }
        }
    }
}

private val CARD_SHAPE = RoundedCornerShape(24.dp)

private fun resolveSiteHost(site: PinnedSite): String =
    site.host.ifBlank {
        runCatching { Uri.parse(site.url).host?.removePrefix("www.").orEmpty() }.getOrDefault("")
    }

private fun loadSavedSiteIcon(context: Context, host: String): Bitmap? {
    if (host.isBlank()) return null
    return runCatching {
        val clazz = SiteIcons::class.java
        val method = clazz.declaredMethods.firstOrNull { m ->
            m.parameterCount == 2 &&
                m.parameterTypes[0].isAssignableFrom(Context::class.java) &&
                m.parameterTypes[1] == String::class.java &&
                Bitmap::class.java.isAssignableFrom(m.returnType)
        }
        method?.invoke(SiteIcons, context.applicationContext, host) as? Bitmap
    }.getOrNull()
}

@Composable
private fun QuickDestinationsCard(
    frame: CarEnvironment.Frame,
    pinnedSites: List<PinnedSite>,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalCarNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier
            .gsPanelShadow(CARD_SHAPE)
            .border(1.dp, Color.White.copy(alpha = 0.55f), CARD_SHAPE),
        shape = CARD_SHAPE,
        color = Color.White.copy(alpha = 0.66f),
        contentColor = Color(0xFF141722),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShortcutCircleButton(
                label = "Home",
                icon = Icons.Filled.Home,
                circleColor = Color(0xFF007AFF),
                onClick = { scope.launch { CarServices.reconnectNav(frame) } },
            )
            ShortcutCircleButton(
                label = "Work",
                icon = Icons.Filled.LocationOn,
                circleColor = Color(0xFFFF9500),
                onClick = { scope.launch { CarServices.reconnectNav(frame) } },
            )
            ShortcutCircleButton(
                label = "Apps",
                icon = MediaIcons.Apps,
                circleColor = Color(0xFF34C759),
                onClick = { navigator.apps() },
            )
            pinnedSites.forEach { site ->
                val host = resolveSiteHost(site)
                val siteIcon by produceState<Bitmap?>(initialValue = null, key1 = host, key2 = site.url) {
                    value = loadSavedSiteIcon(context, host)
                }
                PinnedSiteCircleButton(
                    label = site.title.ifBlank { host.ifBlank { site.url } },
                    iconBitmap = siteIcon,
                    onClick = { PinnedWebSites.openInBrowser(navigator, site.url) },
                )
            }
        }
    }
}

@Composable
private fun PinnedSiteCircleButton(
    label: String,
    iconBitmap: Bitmap?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF007AFF).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = label,
                    tint = Color(0xFF007AFF),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF141722),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShortcutCircleButton(
    label: String,
    icon: ImageVector,
    circleColor: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF141722),
            maxLines = 1,
        )
    }
}

@Composable
private fun MapPane(status: CarAppConnection.Status, frame: CarEnvironment.Frame) {
    val navigator = LocalCarNavigator.current
    if (status.phase == CarAppConnection.Phase.RUNNING) {
        CarAppStage(CarServices.nav, status, frame, embedded = true) { CarServices.stopNav() }
        return
    }

    val hasLast = CarSettings.lastNav.value != null
    val hasNavApps by CarServices.hasNavApps.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF007AFF).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ImageVector.vectorResource(R.drawable.navigation_24),
                contentDescription = null,
                tint = Color(0xFF007AFF),
                modifier = Modifier.size(34.dp),
            )
        }

        when (status.phase) {
            CarAppConnection.Phase.BINDING, CarAppConnection.Phase.HANDSHAKE ->
                Text(
                    status.app?.let { stringResource(R.string.home_starting, it) }
                        ?: stringResource(R.string.home_starting_map),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF141722),
                )
            CarAppConnection.Phase.REJECTED, CarAppConnection.Phase.FAILED -> {
                Text(
                    status.app?.let { stringResource(R.string.home_failed, it) }
                        ?: stringResource(R.string.home_failed_map),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF141722),
                )
                StatusButton(stringResource(R.string.home_choose_app), GsTone.Primary, navigator::apps)
            }
            else -> if (!hasNavApps) {
                Text(
                    stringResource(R.string.home_no_maps),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF141722),
                )
                Text(
                    stringResource(R.string.home_no_maps_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF141722).copy(alpha = 0.72f),
                )
            } else if (hasLast) {
                Text(
                    stringResource(R.string.home_map_stopped),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF141722),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusButton(stringResource(R.string.home_reopen_map), GsTone.Primary) {
                        scope.launch { CarServices.reconnectNav(frame) }
                    }
                    StatusButton(stringResource(R.string.home_car_apps), GsTone.Tonal) { navigator.apps() }
                }
            } else {
                Text(
                    "Navigation Ready",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF141722),
                )
            }
        }
    }
}

@Composable
private fun StatusButton(label: String, tone: GsTone, onClick: () -> Unit) {
    GsButton(onClick = onClick, tone = tone, modifier = Modifier.width(176.dp).height(52.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CarPlayMediaCard(now: NowPlaying, modifier: Modifier) {
    val navigator = LocalCarNavigator.current
    val media = CarServices.media
    val context = LocalContext.current
    val art by produceState(now.art, now.art, now.artUri) { value = now.art ?: MediaArt.load(context, now.artUri) }

    Surface(
        modifier = modifier
            .gsPanelShadow(CARD_SHAPE)
            .border(1.dp, Color.White.copy(alpha = 0.55f), CARD_SHAPE),
        shape = CARD_SHAPE,
        color = Color.White.copy(alpha = 0.68f),
        contentColor = Color(0xFF141722),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Album Art + Song Title & Artist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { navigator.media() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val coverShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(coverShape)
                        .background(Color(0xFF007AFF).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(art, animationSpec = tween(300), label = "cover") { shown ->
                        if (shown != null) {
                            Image(
                                shown.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                MediaIcons.MusicNote,
                                contentDescription = null,
                                tint = Color(0xFF007AFF),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = now.title.ifBlank { stringResource(R.string.media_nothing_playing) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF141722),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = now.artist.ifBlank { "Tap to open Media" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF141722).copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (now.durationMs > 0) {
                PlayerProgress(
                    now,
                    media,
                    Modifier.fillMaxWidth(),
                    timeStyle = MaterialTheme.typography.labelMedium,
                )
            }

            // CarPlay-style clean Previous / Play-Pause / Next row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GsIconButton(
                    MediaIcons.Previous,
                    stringResource(R