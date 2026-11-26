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
                        }
                        addJavascriptInterface(
                            FieldFocusBridge(onFocus = { fieldText = it }, onBlur = { fieldText = null }),
                            "GearslipField",
                        )
                        val initial = PinnedWebSites.consumePendingUrl() ?: content
                        if (initial.isNotBlank()) {
                            atStart = false
                            loadContent(this, initial)
                        }
                        web = this
                    }
                },
                update = { it.settings.isAlgorithmicDarkeningAllowed = darkOutside },
                onRelease = { it.destroy() },
            )
            if (atStart) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    BrowserStartPage(
                        onOpen = open,
                        onSearch = {
                            fieldText = null
                            address = ""
                        },
                        onAdd = { newBookmark = "https://" },
                    )
                }
            }
            pageFullscreen?.let { (view, _) ->
                androidx.compose.runtime.key(view) {
                    AndroidView(
                        factory = { context ->
                            FrameLayout(context).apply {
                                setBackgroundColor(android.graphics.Color.BLACK)
                                (view.parent as? ViewGroup)?.removeView(view)
                                addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { it.removeAllViews() },
                    )
                }
            }

            if (hideChromeForFullscreen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(52.dp)
                        .pointerInput(Unit) {
                            var totalDragY = 0f
                            detectVerticalDragGestures(
                                onDragStart = { totalDragY = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    totalDragY += dragAmount
                                    if (totalDragY > 24f && !showExitFullscreenPill) {
                                        showExitFullscreenPill = true
                                        exitPillTriggerCount++
                                    }
                                },
                            )
                        },
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = showExitFullscreenPill,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xDD14161D))
                            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                            .clickable { exitAllFullscreen() }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Exit fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Exit fullscreen",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(52.dp)
                    .pointerInput(Unit) {
                        var upwardDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { upwardDrag = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount < 0f) {
                                    upwardDrag += -dragAmount
                                    if (upwardDrag > 24f && !showBrightnessBoosterSheet) {
                                        showBrightnessPill = true
                                        brightnessPillTriggerCount++
                                    }
                                }
                            },
                        )
                    },
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = showBrightnessPill && !showBrightnessBoosterSheet,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xEE14161D))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                        .clickable {
                            showBrightnessPill = false
                            showBrightnessBoosterSheet = true
                            boosterInteractionTick++
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Open Brightness Booster",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Open Brightness Booster",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showBrightnessBoosterSheet,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                val pct = (brightnessLevel * 100).toInt()
                val boosted = brightnessLevel >= 0.85f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { showBrightnessBoosterSheet = false },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(bottom = 20.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xF2181B24))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                            .clickable(enabled = false) {}
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = if (boosted) Color(0xFFFFC107) else Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = if (boosted) "Brightness Booster · $pct% (BOOST)" else "Brightness Booster · $pct%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { showBrightnessBoosterSheet = false },
                            )
                        }

                        Slider(
                            value = brightnessLevel,
                            onValueChange = { newVal ->
                                brightnessLevel = newVal.coerceIn(0.1f, 1.0f)
                                applyWindowBrightness(context, brightnessLevel)
                                boosterInteractionTick++
                            },
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .clickable {
                                        brightnessLevel = 0.5f
                                        applyWindowBrightness(context, brightnessLevel)
                                        boosterInteractionTick++
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("50%", color = Color.White, fontWeight = FontWeight.Medium)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .clickable {
                                        brightnessLevel = 0.8f
                                        applyWindowBrightness(context, brightnessLevel)
                                        boosterInteractionTick++
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("80%", color = Color.White, fontWeight = FontWeight.Medium)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFC107))
                                    .clickable {
                                        brightnessLevel = 1.0f
                                        applyWindowBrightness(context, brightnessLevel)
                                        boosterInteractionTick++
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("100% Boost", color = Color(0xFF14161D), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        address?.let { typed ->
            CarKeyboard(
                text = typed,
                onTextChange = { address = it },
                onSubmit = {
                    open(BrowserSettings.addressToUrl(typed))
                    address = null
                },
                onDismiss = { address = null },
            )
        }
        newBookmark?.let { typed ->
            CarKeyboard(
                text = typed,
                onTextChange = { newBookmark = it },
                onSubmit = {
                    val text = typed.trim()
                    val url = when {
                        text.startsWith("http://") || text.startsWith("https://") -> text
                        ' ' !in text && '.' in text -> "https://$text"
                        else -> null
                    }
                    if (url != null && BrowserSettings.addBookmark("", url)) {
                        CarToasts.show(BrowserSettings, context.getString(R.string.web_bookmark_added), CarToast.LENGTH_SHORT)
                    }
                    newBookmark = null
                },
                onDismiss = { newBookmark = null },
            )
        }
        fieldText?.let { text ->
            CarKeyboard(
                text = text,
                onTextChange = { updated ->
                    fieldText = updated
                    web?.evaluateJavascript(setFieldValueJs(updated), null)
                },
                onSubmit = { web?.evaluateJavascript(SUBMIT_FIELD_JS, null) },
                onDismiss = {
                    fieldText = null
                    web?.evaluateJavascript(BLUR_FIELD_JS, null)
                },
            )
        }
    }
}

private fun loadContent(view: WebView, content: String) {
    if (content.startsWith("http://") || content.startsWith("https://")) {
        view.loadUrl(content)
    } else {
        view.loadDataWithBaseURL(null, content, "text/html", "utf-8", null)
    }
}

private class LoggingClient(
    private val ring: String,
    private val documentUrl: AtomicReference<String?>,
    private val onStarted: (String?) -> Unit,
    private val onLoaded: (WebView) -> Unit,
) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
        request?.let { AdBlockManager.response(it, documentUrl.get()) }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        documentUrl.set(url)
        onStarted(url)
        if (CosmeticFilterBridge.needsPageStartFallback) {
            view?.evaluateJavascript(CosmeticFilterBridge.INJECTION_JS, null)
            view?.evaluateJavascript(YouTubeAdBlock.INJECTION_JS, null)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        documentUrl.set(url)
        GearslipLog.i("car web page loaded: $url")
        AdBlockManager.logPageCost(url)
        view?.let(onLoaded)
        view?.evaluateJavascript(FOCUS_TRACKING_JS, null)
        view?.evaluateJavascript(focusRingJs(ring), null)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) {
            GearslipLog.e("car web page FAILED: ${request.url} - ${error?.errorCode} ${error?.description}")
        }
    }

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
        if (request?.isForMainFrame == true) {
            GearslipLog.w("car web page HTTP ${response?.statusCode} for ${request.url}")
        }
    }
}

private class FieldFocusBridge(private val onFocus: (String) -> Unit, private val onBlur: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun focused(value: String) { main.post { onFocus(value) } }

    @JavascriptInterface
    fun blurred() { main.post { onBlur() } }
}

private fun setFieldValueJs(value: String) = """
    (function() {
        var el = document.activeElement;
        if (!el) return;
        var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
        var setter = Object.getOwnPropertyDescriptor(proto, 'value');
        if (setter && setter.set) { setter.set.call(el, ${JSONObject.quote(value)}); } else { el.value = ${JSONObject.quote(value)}; }
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
    })();
""".trimIndent()

private const val BLUR_FIELD_JS = "document.activeElement && document.activeElement.blur();"

private const val SUBMIT_FIELD_JS = """
    (function() {
        var el = document.activeElement;
        if (!el) return;
        var enter = new KeyboardEvent('keydown', {
            key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true,
        });
        var notHandled = el.dispatchEvent(enter);
        if (notHandled && el.form) {
            if (el.form.requestSubmit) el.form.requestSubmit(); else el.form.submit();
        }
    })();
"""

private fun focusRingJs(color: String) = """
    (function() {
        if (document.getElementById('gearslip-focus-ring')) return;
        var style = document.createElement('style');
        style.id = 'gearslip-focus-ring';
        style.textContent = ':focus-visible { outline: 3px solid $color !important; outline-offset: 2px !important; }';
        (document.head || document.documentElement).appendChild(style);
    })();
"""

private const val FOCUS_TRACKING_JS = """
    (function() {
        function isEditable(el) {
            if (!el) return false;
            var tag = el.tagName;
            if (tag === 'TEXTAREA') return true;
            if (el.isContentEditable) return true;
            if (tag !== 'INPUT') return false;
            var skip = ['button', 'submit', 'checkbox', 'radio', 'range', 'color', 'file', 'image', 'reset'];
            return skip.indexOf((el.type || 'text').toLowerCase()) === -1;
        }
        document.addEventListener('focusin', function(e) {
            if (isEditable(e.target)) window.GearslipField.focused(e.target.value || '');
        });
        document.addEventListener('focusout', function(e) {
            if (isEditable(e.target)) window.GearslipField.blurred();
        });
    })();
"""