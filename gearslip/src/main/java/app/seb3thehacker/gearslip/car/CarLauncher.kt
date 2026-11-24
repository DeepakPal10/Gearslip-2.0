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