package app.seb3thehacker.gearslip.car

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.car.app.CarToast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.seb3thehacker.gearslip.GearslipLog
import app.seb3thehacker.gearslip.R
import app.seb3thehacker.gearslip.browser.BrowserSettings
import app.seb3thehacker.gearslip.browser.SiteIcons
import app.seb3thehacker.gearslip.browser.adblock.AdBlockManager
import app.seb3thehacker.gearslip.browser.adblock.CosmeticFilterBridge
import app.seb3thehacker.gearslip.browser.adblock.YouTubeAdBlock
import app.seb3thehacker.gearslip.car.theme.GsIconBox
import app.seb3thehacker.gearslip.car.theme.GsIconButton
import app.seb3thehacker.gearslip.car.theme.GsTone
import app.seb3thehacker.gearslip.car.theme.gsColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

private const val PINNED_PREFS = "gearslip_pinned_sites"
private const val PINNED_KEY = "sites_json"

internal data class PinnedSite(
    val title: String,
    val url: String,
    val host: String = "",
)

private fun Any?.toImageBitmapOrNull(): ImageBitmap? = when (this) {
    is ImageBitmap -> this
    is Bitmap -> runCatching { asImageBitmap() }.getOrNull()
    else -> null
}

internal object PinnedWebSites {
    private val pendingUrl = MutableStateFlow<String?>(null)
    val pendingOpenUrl: StateFlow<String?> = pendingUrl.asStateFlow()

    private val sitesFlow = MutableStateFlow<List<PinnedSite>>(emptyList())
    val sites: StateFlow<List<PinnedSite>> = sitesFlow.asStateFlow()

    private var initialized = false

    private fun extractHost(url: String): String =
        runCatching { android.net.Uri.parse(url).host?.removePrefix("www.").orEmpty() }.getOrDefault("")

    fun ensureLoaded(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.applicationContext.getSharedPreferences(PINNED_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(PINNED_KEY, null) ?: return
        val parsed = runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val u = obj.optString("url").trim()
                    if (u.startsWith("http://") || u.startsWith("https://")) {
                        val h = obj.optString("host").trim().ifEmpty { extractHost(u) }
                        val t = obj.optString("title").trim().ifEmpty { h.ifEmpty { u } }
                        add(PinnedSite(title = t, url = u, host = h))
                    }
                }
            }
        }.getOrDefault(emptyList())
        sitesFlow.value = parsed
    }

    private fun persist(context: Context, list: List<PinnedSite>) {
        val arr = JSONArray()
        list.forEach { site ->
            arr.put(
                JSONObject()
                    .put("title", site.title)
                    .put("url", site.url)
                    .put("host", site.host.ifEmpty { extractHost(site.url) }),
            )
        }
        context.applicationContext
            .getSharedPreferences(PINNED_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PINNED_KEY, arr.toString())
            .apply()
    }

    fun isPinned(url: String?): Boolean {
        val target = url?.trim().orEmpty()
        if (target.isEmpty()) return false
        return sitesFlow.value.any { it.url == target }
    }

    fun pin(context: Context, title: String, url: String): Boolean {
        ensureLoaded(context)
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) return false
        val cleanHost = extractHost(cleanUrl)
        val cleanTitle = title.trim().ifEmpty { cleanHost.ifEmpty { cleanUrl } }
        val current = sitesFlow.value.filterNot { it.url == cleanUrl }
        val updated = current + PinnedSite(title = cleanTitle, url = cleanUrl, host = cleanHost)
        sitesFlow.value = updated
        persist(context, updated)
        return true
    }

    fun unpin(context: Context, url: String): Boolean {
        ensureLoaded(context)
        val cleanUrl = url.trim()
        val updated = sitesFlow.value.filterNot { it.url == cleanUrl }
        if (updated.size == sitesFlow.value.size) return false
        sitesFlow.value = updated
        persist(context, updated)
        return true
    }

    fun openInBrowser(navigator: CarNavigator, url: String) {
        pendingUrl.value = url
        BuiltInApps.Web.open(navigator)
    }

    fun openAaBrowserMode(navigator: CarNavigator, url: String = "https://www.google.com") {
        openInBrowser(navigator, url)
    }

    fun consumePendingUrl(): String? {
        val u = pendingUrl.value
        pendingUrl.value = null
        return u
    }

    private fun loadSavedIcon(context: Context, host: String): Any? {
        if (host.isBlank()) return null
        return runCatching {
            val clazz = SiteIcons::class.java
            val method = clazz.declaredMethods.firstOrNull { m ->
                m.parameterCount == 2 &&
                    m.parameterTypes[0].isAssignableFrom(Context::class.java) &&
                    m.parameterTypes[1] == String::class.java
            }
            method?.invoke(SiteIcons, context.applicationContext, host)
        }.getOrNull()
    }

    internal fun entries(context: Context): List<Entry> {
        ensureLoaded(context)
        return sitesFlow.value.map { site ->
            val cleanHost = site.host.ifEmpty { extractHost(site.url) }
            val imageBitmap = loadSavedIcon(context, cleanHost).toImageBitmapOrNull()
            Entry(
                label = site.title,
                icon = imageBitmap,
                pinnedUrl = site.url,
                fallbackGlyph = Icons.Filled.Home,
            )
        }
    }
}

private fun applyDesktopMode(view: WebView?, enabled: Boolean, defaultUserAgent: String?) {
    val wv = view ?: return
    val targetUa = if (enabled) DESKTOP_USER_AGENT else defaultUserAgent
    wv.settings.useWideViewPort = true
    wv.settings.loadWithOverviewMode = true
    val uaChanged = targetUa != null && wv.settings.userAgentString != targetUa
    if (uaChanged) {
        wv.settings.userAgentString = targetUa
    }
    if (wv.url != null && wv.url != "about:blank") {
        wv.reload()
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun applyWindowBrightness(context: Context, level: Float) {
    val clamped = level.coerceIn(0.1f, 1.0f)
    val activity = context.findActivity() ?: return
    runCatching {
        val attrs = activity.window.attributes
        attrs.screenBrightness = clamped
        activity.window.attributes = attrs
    }
}

@Composable
fun WebApp(content: String) {
    val navigator = LocalCarNavigator.current
    val darkOutside = LocalDarkOutside.current
    val ring = String.format("#%06X", MaterialTheme.colorScheme.primary.toArgb() and 0xFFFFFF)
    var web by remember { mutableStateOf<WebView?>(null) }
    var defaultUserAgent by remember { mutableStateOf<String?>(null) }
    var desktopMode by remember { mutableStateOf(false) }
    var fieldText by remember { mutableStateOf<String?>(null) }
    var pageFullscreen by remember { mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null) }
    var videoMadeFullscreen by remember { mutableStateOf(false) }
    val hideChromeForFullscreen = CarFullscreen.active || pageFullscreen != null
    var showExitFullscreenPill by remember { mutableStateOf(false) }
    var exitPillTriggerCount by remember { mutableIntStateOf(0) }
    var showBrightnessPill by remember { mutableStateOf(false) }
    var brightnessPillTriggerCount by remember { mutableIntStateOf(0) }
    var showBrightnessBoosterSheet by remember { mutableStateOf(false) }
    var boosterInteractionTick by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf("") }
    var canGoForward by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf<String?>(null) }
    var newBookmark by remember { mutableStateOf<String?>(null) }
    var atStart by remember { mutableStateOf(content.isBlank()) }
    var pageUrl by remember { mutableStateOf<String?>(null) }
    val bookmarks by BrowserSettings.bookmarks.collectAsState()
    val pinnedSites by PinnedWebSites.sites.collectAsState()
    val pendingPinnedUrl by PinnedWebSites.pendingOpenUrl.collectAsState()
    val context = LocalContext.current
    var brightnessLevel by remember {
        mutableFloatStateOf(
            runCatching {
                val cur = context.findActivity()?.window?.attributes?.screenBrightness ?: -1f
                if (cur in 0.1f..1.0f) cur else 0.75f
            }.getOrDefault(0.75f),
        )
    }

    LaunchedEffect(Unit) {
        PinnedWebSites.ensureLoaded(context)
        AdBlockManager.ensureInitialized(context)
        AdBlockManager.refreshIfDue(context)
    }

    LaunchedEffect(hideChromeForFullscreen) {
        if (!hideChromeForFullscreen) {
            showExitFullscreenPill = false
        }
    }

    LaunchedEffect(showExitFullscreenPill, exitPillTriggerCount) {
        if (showExitFullscreenPill) {
            delay(3500L)
            showExitFullscreenPill = false
        }
    }

    LaunchedEffect(showBrightnessPill, brightnessPillTriggerCount) {
        if (showBrightnessPill) {
            delay(3500L)
            showBrightnessPill = false
        }
    }

    LaunchedEffect(showBrightnessBoosterSheet, boosterInteractionTick) {
        if (showBrightnessBoosterSheet) {
            delay(4000L)
            showBrightnessBoosterSheet = false
        }
    }

    val exitAllFullscreen: () -> Unit = {
        showExitFullscreenPill = false
        pageFullscreen?.second?.onCustomViewHidden()
        pageFullscreen = null
        if (CarFullscreen.active) {
            CarFullscreen.exit()
        }
    }

    LaunchedEffect(desktopMode, web, defaultUserAgent) {
        if (web != null && defaultUserAgent != null) {
            applyDesktopMode(web, desktopMode, defaultUserAgent)
        }
    }

    val open: (String) -> Unit = { url ->
        atStart = false
        web?.loadUrl(url)
    }

    LaunchedEffect(pendingPinnedUrl, web) {
        val target = pendingPinnedUrl
        if (target != null && web != null) {
            PinnedWebSites.consumePendingUrl()
            open(target)
        }
    }

    val goHome: () -> Unit = {
        if (content.isBlank()) {
            atStart = true
            pageTitle = ""
        } else {
            web?.let { view -> loadContent(view, content) }
        }
    }

    DisposableEffect(Unit) { onDispose { CarFullscreen.exit() } }
    LaunchedEffect(CarFullscreen.active) {
        if (!CarFullscreen.active) pageFullscreen?.let { (_, callback) ->
            pageFullscreen = null
            callback.onCustomViewHidden()
        }
    }

    DisposableEffect(web, atStart, pageFullscreen) {
        navigator.backInterceptor = {
            val view = web
            if (showBrightnessBoosterSheet) {
                showBrightnessBoosterSheet = false
                true
            } else if (pageFullscreen != null || CarFullscreen.active) {
                exitAllFullscreen()
                true
            } else if (!atStart && view != null && view.canGoBack()) {
                view.goBack()
                true
            } else if (!atStart && content.isBlank()) {
                goHome()
                true
            } else {
                false
            }
        }
        onDispose { navigator.backInterceptor = null }
    }

    Column(Modifier.fillMaxSize()) {
        if (!hideChromeForFullscreen) {
            ScreenHeader(pageTitle.takeUnless { atStart }.orEmpty().ifEmpty { stringResource(R.string.app_browser) }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val url = pageUrl
                if (!atStart && url != null && url.startsWith("http")) {
                    val saved = remember(bookmarks, url) { BrowserSettings.isBookmarked(url) }
                    val pinned = remember(pinnedSites, url) { PinnedWebSites.isPinned(url) }
                    GsIconBox(
                        {
                            if (saved) BrowserSettings.removeBookmark(url) else BrowserSettings.addBookmark(pageTitle, url)
                            CarToasts.show(BrowserSettings, context.getString(if (saved) R.string.web_bookmark_removed else R.string.web_bookmark_added), CarToast.LENGTH_SHORT)
                        },
                        Modifier.size(48.dp),
                        gsColors(if (saved) GsTone.Tonal else GsTone.Neutral),
                        latched = saved,
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            stringResource(if (saved) R.string.web_remove_this_bookmark else R.string.web_bookmark_this),
                            Modifier.size(24.dp),
                            tint = if (saved) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.6f),
                        )
                    }
                    GsIconBox(
                        {
                            val done = if (pinned) PinnedWebSites.unpin(context, url) else PinnedWebSites.pin(context, pageTitle, url)
                            if (done) {
                                val msg = if (pinned) "Unpinned from Home" else "Pinned to Home"
                                CarToasts.show(BrowserSettings, msg, CarToast.LENGTH_SHORT)
                            }
                        },
                        Modifier.size(48.dp),
                        gsColors(if (pinned) GsTone.Tonal else GsTone.Neutral),
                        latched = pinned,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = if (pinned) "Unpin from Home" else "Pin to Home",
                            Modifier.size(24.dp),
                            tint = if (pinned) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.65f),
                        )
                    }
                }
                if (!atStart) {
                    GsIconBox(
                        {
                            desktopMode = !desktopMode
                            val msg = if (desktopMode) "Desktop mode ON" else "Desktop mode OFF"
                            CarToasts.show(BrowserSettings, msg, CarToast.LENGTH_SHORT)
                        },
                        Modifier.size(48.dp),
                        gsColors(if (desktopMode) GsTone.Tonal else GsTone.Neutral),
                        latched = desktopMode,
                    ) {
                        Icon(
                            Icons.Filled.Home,
                            contentDescription = "Desktop mode",
                            Modifier.size(24.dp),
                            tint = if (desktopMode) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.7f),
                        )
                    }
                }
                GsIconButton(Icons.Filled.Search, stringResource(R.string.web_search), {
                    fieldText = null
                    address = ""
                })
                if (canGoForward && !atStart) {
                    GsIconButton(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.web_forward), { web?.goForward() })
                }
                if (!atStart) {
                    GsIconButton(Icons.Filled.Refresh, stringResource(R.string.web_reload), { web?.reload() })
                    GsIconButton(Icons.Filled.Home, stringResource(R.string.web_start_page), goHome)
                    GsIconButton(MediaIcons.Fullscreen, stringResource(R.string.web_fullscreen), CarFullscreen::enter)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        val documentUrl = AtomicReference<String?>(null)
                        CosmeticFilterBridge.attach(this)
                        defaultUserAgent = settings.userAgentString
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        if (desktopMode) {
                            settings.userAgentString = DESKTOP_USER_AGENT
                        }
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.offscreenPreRaster = true
                        webViewClient = LoggingClient(ring, documentUrl, onStarted = { pageUrl = it }) { view ->
                            canGoForward = view.canGoForward()
                            pageUrl = view.url
                            pageTitle = view.title.orEmpty().takeUnless { it.startsWith("http") || it == "about:blank" }.orEmpty()
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                pageTitle = title.orEmpty().takeUnless { it.startsWith("http") || it == "about:blank" }.orEmpty()
                            }

                            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                                val host = view?.url?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() }?.removePrefix("www.")
                                if (icon != null && host != null) SiteIcons.save(view.context, host, icon)
                            }

                            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                videoMadeFullscreen = !CarFullscreen.active
                                pageFullscreen = view to callback
                                CarFullscreen.enter()
                            }

                            override fun onHideCustomView() {
                                if (pageFullscreen == null) return
                                pageFullscreen = null
                                if (videoMadeFullscreen) CarFullscreen.exit()
                            }