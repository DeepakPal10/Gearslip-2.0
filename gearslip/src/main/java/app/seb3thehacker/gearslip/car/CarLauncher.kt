package app.seb3thehacker.gearslip.car

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.seb3thehacker.gearslip.AppSettings
import app.seb3thehacker.gearslip.R
import app.seb3thehacker.gearslip.car.theme.GsColors
import app.seb3thehacker.gearslip.car.theme.GsIconBox
import app.seb3thehacker.gearslip.host.CarAppCatalog
import app.seb3thehacker.gearslip.host.KnownApps
import app.seb3thehacker.gearslip.host.TemplateApp
import app.seb3thehacker.gearslip.media.MediaApp
import app.seb3thehacker.gearslip.media.MediaCatalog
import app.seb3thehacker.gearslip.notify.MessagingApp
import app.seb3thehacker.gearslip.notify.MessagingCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

private const val GRID_COLUMNS = 4
private const val GRID_ROWS = 2
private const val APPS_PER_PAGE = GRID_COLUMNS * GRID_ROWS

private val AA_BROWSER_PACKAGES = listOf(
    "app.aabrowser",
    "com.fcaronte.aabrowser",
    "fcaronte.aabrowser",
)

internal const val CHROME_ENTRY_ID = "builtin:chrome"
internal const val CHROME_LABEL = "Chrome"
private const val ANDROID_CHROME_PACKAGE = "com.android.chrome"

private fun isChromeEntry(entry: Entry): Boolean =
    entry.label == CHROME_LABEL || entry.customPackage == CHROME_ENTRY_ID

private fun chromeEntry(context: Context): Entry {
    val chromeIcon = runCatching {
        context.packageManager.getApplicationIcon(ANDROID_CHROME_PACKAGE)
            .toBitmap(128, 128)
            .asImageBitmap()
    }.getOrNull()
    return Entry(
        label = CHROME_LABEL,
        icon = chromeIcon,
        customPackage = CHROME_ENTRY_ID,
    )
}

internal fun launchChromeInBuilt(
    navigator: CarNavigator,
    url: String = "https://www.google.com",
) {
    runCatching { PinnedWebSites.openAaBrowserMode(navigator, url) }
        .onFailure {
            runCatching { PinnedWebSites.openInBrowser(navigator, url) }
                .onFailure { BuiltInApps.Web.open(navigator) }
        }
}

private class Tile(
    val label: String,
    val icon: ImageBitmap? = null,
    val glyph: ImageVector? = null,
    val accentId: String? = null,
    val broken: Boolean = false,
    val onLongClick: (() -> Unit)? = null,
    val id: String? = null,
    val onClick: () -> Unit,
)

internal class Entry(
    val label: String,
    val icon: ImageBitmap?,
    val template: TemplateApp? = null,
    val media: MediaApp? = null,
    val builtIn: BuiltInApp? = null,
    val messaging: MessagingApp? = null,
    val customPackage: String? = null,
    val pinnedUrl: String? = null,
    val fallbackGlyph: ImageVector? = null,
) {
    val componentId: String?
        get() = builtIn?.let { BUILT_IN_PREFIX + it.id }
            ?: messaging?.let { MESSAGING_PREFIX + it.packageName }
            ?: customPackage?.let { CUSTOM_APP_PREFIX + it }
            ?: pinnedUrl?.let { PINNED_SITE_PREFIX + it }
            ?: (template?.component ?: media?.component)?.flattenToString()

    val packageName: String?
        get() = customPackage ?: template?.component?.packageName ?: media?.component?.packageName ?: messaging?.packageName
}

internal const val MESSAGING_PREFIX = "messages:"
internal const val CUSTOM_APP_PREFIX = "custom:"
internal const val PINNED_SITE_PREFIX = "pinned:"

internal object LauncherCache {
    private val flow = MutableStateFlow<List<Entry>?>(null)
    val state: StateFlow<List<Entry>?> = flow.asStateFlow()
    val entries: List<Entry>? get() = flow.value

    @Synchronized
    fun load(context: Context, force: Boolean = false): List<Entry> {
        if (!force) entries?.let { return it }
        fun iconOf(pkg: String) = runCatching {
            context.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap()
        }.getOrNull()

        val carApps = CarAppCatalog.installed(context).associateBy { it.component.packageName }
        val mediaApps = MediaCatalog.installed(context).associateBy { it.component.packageName }
        val messagingApps = MessagingCatalog.installed(context).associateBy { it.packageName }

        val tiles = (carApps.keys + mediaApps.keys + messagingApps.keys).flatMap { pkg ->
            val icon by lazy { iconOf(pkg) }
            tilesFor(carApps[pkg], mediaApps[pkg], messagingApps[pkg], context.getString(R.string.media_browse)).map { tile ->
                when (tile) {
                    is AppTile.Screen -> Entry(tile.label, icon, template = tile.app)
                    is AppTile.Player -> Entry(tile.app.label, icon, media = tile.app)
                    is AppTile.Messages -> Entry(tile.app.label, icon, messaging = tile.app)
                }
            }
        }
        CarSettings.hideOnFirstSight(tiles.mapNotNull { entry ->
            val pkg = (entry.template?.component ?: entry.media?.component)?.packageName ?: entry.messaging?.packageName
            entry.componentId?.takeIf { pkg != null && KnownApps.startsHidden(context, pkg, entry.messaging != null) }
        })
        return tiles.sortedBy { it.label.lowercase() }.also { flow.value = it }
    }
}

private sealed interface AppTile {
    class Screen(val app: TemplateApp, val label: String) : AppTile
    class Player(val app: MediaApp) : AppTile
    class Messages(val app: MessagingApp) : AppTile
}

private fun tilesFor(
    car: TemplateApp?,
    media: MediaApp?,
    messaging: MessagingApp?,
    browse: String,
): List<AppTile> {
    if (car != null && !car.isMediaScreen && car.kind != "messaging") return listOf(AppTile.Screen(car, car.label))
    val pkg = car?.component?.packageName ?: media?.component?.packageName ?: messaging?.packageName ?: return emptyList()
    return buildList {
        val screenRefused = KnownApps.screenBroken(pkg) && KnownApps.playerWorks(pkg)
        val messengerScreen = messaging != null && (car?.kind == "messaging" || car?.kind == "app")
        if (car != null && !messengerScreen && !(media != null && screenRefused)) {
            add(AppTile.Screen(car, if (media != null) "${car.label} ·$browse" else car.label))
        }
        if (media != null) add(AppTile.Player(media))
        else if (messaging != null) add(AppTile.Messages(messaging))
    }
}

@Composable
internal fun rememberAllEntries(): List<Entry> {
    val context = LocalContext.current
    val installed by LauncherCache.state.collectAsState()
    val launcher by LauncherMode.active.collectAsState()
    val pinnedSites by PinnedWebSites.sites.collectAsState()
    LaunchedEffect(context) {
        PinnedWebSites.ensureLoaded(context)
        if (LauncherCache.entries == null) withContext(Dispatchers.IO) { LauncherCache.load(context) }
    }
    return remember(installed, launcher, pinnedSites) {
        val base = BuiltInApps.entries(launcher, LauncherMode.canCall(context)) + installed.orEmpty()
        val pinnedEntries = PinnedWebSites.entries(context)
        val combined = base + pinnedEntries
        if (combined.any(::isChromeEntry)) combined else combined + chromeEntry(context)
    }
}

private enum class AppGroup { PHONE, MAPS, MUSIC, MESSAGING, WEB, SCREEN_SHARING, OTHER, SETTINGS, EXIT }

private fun groupOf(entry: Entry, mediaPackages: Set<String>): AppGroup {
    entry.builtIn?.let {
        return when (it) {
            BuiltInApps.Phone -> AppGroup.PHONE
            BuiltInApps.Web -> AppGroup.WEB
            BuiltInApps.ScreenSharing -> AppGroup.SCREEN_SHARING
            BuiltInApps.Exit -> AppGroup.EXIT
            else -> AppGroup.SETTINGS
        }
    }
    if (isChromeEntry(entry) || entry.pinnedUrl != null) return AppGroup.WEB
    if (entry.customPackage != null) return AppGroup.OTHER
    if (entry.media != null) return AppGroup.MUSIC
    if (entry.messaging != null) return AppGroup.MESSAGING
    val app = entry.template ?: return AppGroup.OTHER
    if (app.component.packageName in mediaPackages) return AppGroup.MUSIC
    return when (app.kind) {
        "calling" -> AppGroup.PHONE
        "navigation", "poi", "parking", "charging" -> AppGroup.MAPS
        "media" -> AppGroup.MUSIC
        "messaging" -> AppGroup.MESSAGING
        "settings" -> AppGroup.SETTINGS
        else -> AppGroup.OTHER
    }
}

internal fun launcherOrder(all: List<Entry>, showVehicleData: Boolean, custom: List<String>, hidden: Set<String>): List<Entry> {
    val shown = all.filter { entry ->
        (entry.builtIn !== BuiltInApps.VehicleData || showVehicleData) &&
            (entry.pinnedUrl != null || entry.componentId !in hidden)
    }
    val mediaPackages = shown.mapNotNull { it.media?.component?.packageName }.toSet()
    val grouped = shown.sortedWith(
        compareBy<Entry>({ groupOf(it, mediaPackages) }, { it.builtIn === BuiltInApps.Settings }, { it.label.lowercase() }),
    )
    if (custom.isEmpty()) return grouped
    val rank = custom.withIndex().associate { (i, id) -> id to i }
    fun Entry.rank() = componentId?.let { rank[it] }
    val moved = grouped.filter { it.rank() != null }.sortedBy { it.rank() }.iterator()
    return grouped.map { if (it.rank() != null) moved.next() else it }
}

internal fun findEntry(id: String): Entry? =
    BuiltInApps.everyEntry.find { it.componentId == id } ?: LauncherCache.entries?.find { it.componentId == id }

fun warmLauncherApps(context: Context) {
    LauncherCache.load(context, force = true)
}

internal fun findInstalledAaBrowserPackage(context: Context): String? {
    val pm = context.packageManager
    return AA_BROWSER_PACKAGES.firstOrNull { pkg ->
        runCatching { pm.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)
    }
}

private fun launchPackageDirectly(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

internal fun launchAaBrowser(context: Context, navigator: CarNavigator): Boolean {
    val pkg = findInstalledAaBrowserPackage(context)
    if (pkg != null) {
        launchPackageDirectly(context, pkg)
        return true
    }
    PinnedWebSites.openAaBrowserMode(navigator, "https://www.google.com")
    return false
}

private fun launchCustomOrAaPackage(context: Context, packageName: String, navigator: CarNavigator) {
    if (packageName == CHROME_ENTRY_ID) {
        launchChromeInBuilt(navigator)
        return
    }
    if (packageName in AA_BROWSER_PACKAGES) {
        launchAaBrowser(context, navigator)
        return
    }
    launchPackageDirectly(context, packageName)
}

internal fun launchEntry(entry: Entry, navigator: CarNavigator, frame: CarEnvironment.Frame) {
    if (isChromeEntry(entry)) {
        launchChromeInBuilt(navigator)
        return
    }
    entry.builtIn?.let { it.open(navigator); return }
    entry.pinnedUrl?.let { url ->
        PinnedWebSites.openInBrowser(navigator, url)
        return
    }
    entry.template?.let {
        if (it.isNavigation) {
            CarServices.connectNav(it, frame)
            navigator.home()
        } else {
            CarServices.connectBrowse(it, frame)
            navigator.browse(entry.label)
        }
        return
    }
    entry.media?.let { CarServices.openMedia(it); navigator.media(); return }
    entry.messaging?.let { navigator.messages(it.packageName, it.label); return }
    entry.customPackage?.let { pkg ->
        val ctx = CarEnvironment.appContext ?: return
        launchCustomOrAaPackage(ctx, pkg, navigator)
    }
}

@Composable
internal fun CarPlayWallpaper(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.carplay_wallpaper),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.08f)
                .blur(22.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.06f),
                            Color.Black.copy(alpha = 0.09f),
                            Color.Black.copy(alpha = 0.12f),
                        )
                    )
                )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarLauncher() {
    val context = LocalContext.current
    val navigator = LocalCarNavigator.current
    val frame by CarEnvironment.frame.collectAsState()

    val all = rememberAllEntries()
    val running = rememberRunningApps()
    val showVehicleData = rememberVehicleDataShown()

    fun tileOf(entry: Entry): Tile {
        val id = entry.componentId
        return Tile(
            label = entry.builtIn?.let { context.getString(it.labelRes) } ?: entry.label,
            icon = entry.icon,
            glyph = entry.builtIn?.glyph ?: entry.fallbackGlyph,
            accentId = entry.builtIn?.id ?: entry.label,
            broken = markedBroken(context, entry),
            onLongClick = id?.let { { navigator.showAppMenu(it) } },
            id = id,
        ) {
            id?.let { AppSettings.putString(context, KEY_LAST_APP, it) }
            when {
                isChromeEntry(entry) -> launchChromeInBuilt(navigator)
                entry.pinnedUrl != null -> PinnedWebSites.openInBrowser(navigator, entry.pinnedUrl)
                entry.customPackage != null -> launchCustomOrAaPackage(context, entry.customPackage, navigator)
                else -> openApp(entry, id?.let { running[it] }, navigator, frame)
            }
        }
    }

    val appOrder by CarSettings.appOrder.collectAsState()
    val hidden by CarSettings.hiddenApps.collectAsState()
    val tiles = launcherOrder(all, showVehicleData, appOrder, hidden).map(::tileOf)
    val pages = remember(tiles) {
        if (tiles.isEmpty()) listOf(emptyList()) else tiles.chunked(APPS_PER_PAGE)
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val lastFocus = remember { FocusRequester() }
    val lastId = remember { AppSettings.getString(context, KEY_LAST_APP, "") }
    val lastTileIndex = tiles.indexOfFirst { it.id == lastId }.coerceAtLeast(0)
    val focusIndex by rememberUpdatedState(lastTileIndex)

    DisposableEffect(Unit) {
        var used = false
        val taker = CarKeys.KnobTaker { event ->
            if (used || event == CarKeys.KnobEvent.Back || (event is CarKeys.KnobEvent.Key && !event.down)) {
                return@KnobTaker false
            }
            used = true
            val pageForFocus = (focusIndex / APPS_PER_PAGE).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            scope.launch {
                pagerState.animateScrollToPage(pageForFocus, animationSpec = tween(220))
                withFrameNanos { }
                runCatching { lastFocus.requestFocus() }
            }
            true
        }
        CarKeys.knobTaker = taker
        onDispose { if (CarKeys.knobTaker === taker) CarKeys.knobTaker = null }
    }

    Box(Modifier.fillMaxSize()) {
        CarPlayWallpaper()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 94.dp, end = 24.dp, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                pageSpacing = 18.dp,
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapPositionalThreshold = 0.20f,
                ),
            ) { pageIndex ->
                val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction).absoluteValue
                val pageScale by animateFloatAsState(
                    targetValue = (1f - (pageOffset * 0.03f)).coerceIn(0.96f, 1f),
                    animationSpec = tween(160),
                    label = "pageScale",
                )
                val pageAlpha by animateFloatAsState(
                    targetValue = (1f - (pageOffset * 0.18f)).coerceIn(0.78f, 1f),
                    animationSpec = tween(160),
                    label = "pageAlpha",
                )

                val pageTiles = pages[pageIndex]
                val pageStartIndex = pageIndex * APPS_PER_PAGE

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = pageScale
                            scaleY = pageScale
                            alpha = pageAlpha
                        },
                    verticalArrangement = Arrangement.spacedBy(12.dp