package app.seb3thehacker.gearslip.car

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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

/** One tile on the launcher: either a phone app's own icon or a colorful built-in badge. */
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

/** A launcher app's data, kept apart from [Tile] so it can be cached without capturing click state. */
internal class Entry(
    val label: String,
    val icon: ImageBitmap?,
    val template: TemplateApp? = null,
    val media: MediaApp? = null,
    val builtIn: BuiltInApp? = null,
    val messaging: MessagingApp? = null,
    val customPackage: String? = null,
) {
    val componentId: String?
        get() = builtIn?.let { BUILT_IN_PREFIX + it.id }
            ?: messaging?.let { MESSAGING_PREFIX + it.packageName }
            ?: customPackage?.let { CUSTOM_APP_PREFIX + it }
            ?: (template?.component ?: media?.component)?.flattenToString()

    val packageName: String?
        get() = customPackage ?: template?.component?.packageName ?: media?.component?.packageName ?: messaging?.packageName
}

internal const val MESSAGING_PREFIX = "messages:"
internal const val CUSTOM_APP_PREFIX = "custom:"

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
            add(AppTile.Screen(car, if (media != null) "${car.label} · $browse" else car.label))
        }
        if (media != null) add(AppTile.Player(media))
        else if (messaging != null) add(AppTile.Messages(messaging))
    }
}

private fun customEntriesFor(context: Context, existingPackages: Set<String>): List<Entry> {
    val selected = getSelectedCustomCarApps(context)
    if (selected.isEmpty()) return emptyList()
    val pm = context.packageManager
    return selected
        .filter { it !in existingPackages }
        .mapNotNull { pkg ->
            val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(appInfo).toString().trim() }.getOrDefault(pkg).ifEmpty { pkg }
            val icon = runCatching { pm.getApplicationIcon(appInfo).toBitmap(128, 128).asImageBitmap() }.getOrNull()
            Entry(label = label, icon = icon, customPackage = pkg)
        }
}

@Composable
internal fun rememberAllEntries(): List<Entry> {
    val context = LocalContext.current
    val installed by LauncherCache.state.collectAsState()
    val launcher by LauncherMode.active.collectAsState()
    LaunchedEffect(context) {
        if (LauncherCache.entries == null) withContext(Dispatchers.IO) { LauncherCache.load(context) }
    }
    return remember(installed, launcher) {
        val base = BuiltInApps.entries(launcher, LauncherMode.canCall(context)) + installed.orEmpty()
        val existingPkgs = base.mapNotNull { it.packageName }.toSet()
        base + customEntriesFor(context, existingPkgs)
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
    val shown = all.filter { (it.builtIn !== BuiltInApps.VehicleData || showVehicleData) && it.componentId !in hidden }
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

fun warmLauncherApps(context: Context) { LauncherCache.load(context, force = true) }

internal fun launchEntry(entry: Entry, navigator: CarNavigator, frame: CarEnvironment.Frame) {
    entry.customPackage?.let { pkg ->
        launchCustomCarApp(CarEnvironment.appContext ?: return, pkg)
        return
    }
    entry.builtIn?.let { it.open(navigator); return }
    entry.template?.let {
        if (it.isNavigation) {
            CarServices.connectNav(it, frame)
            navigator.home()
        } else {
            CarServices.connectBrowse(it, frame)
            navigator.browse(entry.label)
        }
    }
    entry.media?.let { CarServices.openMedia(it); navigator.media() }
    entry.messaging?.let { navigator.messages(it.packageName, it.label) }
}

/** Bright Apple CarPlay-style wallpaper with soft blur and airy light glass glow. */
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
                            Color.Black.copy(alpha = 0.12f)
                        )
                    )
                )
        )
    }
}

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
            glyph = entry.builtIn?.glyph,
            accentId = entry.builtIn?.id ?: entry.label,
            broken = markedBroken(context, entry),
            onLongClick = id?.let { { navigator.showAppMenu(it) } },
            id = id,
        ) {
            id?.let { AppSettings.putString(context, KEY_LAST_APP, it) }
            if (entry.customPackage != null) {
                launchCustomCarApp(context, entry.customPackage)
            } else {
                openApp(entry, id?.let { running[it] }, navigator, frame)
            }
        }
    }

    val appOrder by CarSettings.appOrder.collectAsState()
    val hidden by CarSettings.hiddenApps.collectAsState()
    val tiles = launcherOrder(all, showVehicleData, appOrder, hidden).map(::tileOf)

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val lastFocus = remember { FocusRequester() }
    val lastId = remember { AppSettings.getString(context, KEY_LAST_APP, "") }
    val lastIndex = tiles.indexOfFirst { it.id == lastId }.coerceAtLeast(0)
    val focusIndex by rememberUpdatedState(lastIndex)

    DisposableEffect(Unit) {
        var used = false
        val taker = CarKeys.KnobTaker { event ->
            if (used || event == CarKeys.KnobEvent.Back || (event is CarKeys.KnobEvent.Key && !event.down)) {
                return@KnobTaker false
            }
            used = true
            scope.launch {
                gridState.scrollToItem(focusIndex)
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
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 112.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 94.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(tiles) { i, tile ->
                AppTile(tile, if (i == lastIndex) Modifier.focusRequester(lastFocus) else Modifier)
            }
        }
    }
}

internal fun markedBroken(context: Context, entry: Entry): Boolean {
    val pkg = entry.packageName ?: return false
    if (entry.messaging != null) return KnownApps.brokenAsMessenger(context, pkg)
    return KnownApps.isBroken(context, pkg) || (entry.template != null && KnownApps.screenBroken(pkg))
}

@Composable
internal fun BrokenBadge(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(Color(0xFFD32F2F), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(R.string.launcher_broken),
            tint = Color.White,
            modifier = Modifier.size(size * 0.66f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(tile: Tile, modifier: Modifier = Modifier) {
    val squircle = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .height(118.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.22f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        GsIconBox(
            onClick = tile.onClick,
            onLongClick = tile.onLongClick,
            modifier = Modifier.fillMaxSize(),
            colors = GsColors(Color.Transparent, Color.White),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(squircle),
                    contentAlignment = Alignment.Center,
                ) {
                    TileIcon(tile)
                    if (tile.broken) {
                        BrokenBadge(
                            22.dp,
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-2).dp, y = (-2).dp)
                                .border(2.dp, Color.White, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    TileLabel(tile)
                }
            }
        }
    }
}

@Composable
private fun TileIcon(tile: Tile) {
    when {
        tile.icon != null -> Image(
            tile.icon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
        )
        tile.glyph != null -> ColorfulBuiltInIcon(
            idOrLabel = tile.accentId ?: tile.label,
            icon = tile.glyph,
            contentDescription = null,
            boxSize = 64.dp,
            iconSize = 34.dp,
            cornerRadius = 18.dp,
        )
    }
}

@Composable
private fun TileLabel(tile: Tile, maxLines: Int = 1, modifier: Modifier = Modifier) {
    Text(
        tile.label,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFF161820),
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val KEY_LAST_APP = "car_last_app"