package app.seb3thehacker.gearslip.car

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

private const val EXT_PREFS = "gearslip_browser_extensions"
private const val EXT_KEY = "installed_ids"

data class BrowserExtensionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val jsCode: String,
)

object BrowserExtensionsCatalog {
    val items: List<BrowserExtensionItem> = listOf(
        BrowserExtensionItem(
            id = "yt_ambient_light",
            title = "Ambient Light for YouTube",
            subtitle = "Live video color glow around player",
            badge = "YouTube FX",
            primaryColor = Color(0xFF0288D1),
            secondaryColor = Color(0xFF01579B),
            jsCode = """
                (function() {
                    var host = window.location.hostname || '';
                    if (host.indexOf('youtube.com') === -1 && host.indexOf('youtu.be') === -1) return;
                    if (document.getElementById('gs-ext-ambient')) return;
                    var glow = document.createElement('div');
                    glow.id = 'gs-ext-ambient';
                    glow.style.cssText = 'position:fixed;inset:7%;z-index:0;pointer-events:none;border-radius:28px;filter:blur(48px);opacity:0.58;transition:background 0.45s ease;background:radial-gradient(circle,rgba(2,136,209,0.55),rgba(124,77,255,0.28),transparent 75%)';
                    (document.body || document.documentElement).appendChild(glow);
                    var canvas = document.createElement('canvas');
                    canvas.width = 8;
                    canvas.height = 8;
                    var ctx = canvas.getContext('2d', { willReadFrequently: true });
                    if (window.__gsAmbientTimer) clearInterval(window.__gsAmbientTimer);
                    window.__gsAmbientTimer = setInterval(function() {
                        var el = document.getElementById('gs-ext-ambient');
                        if (!el) return;
                        var v = document.querySelector('video');
                        if (!v || v.paused || v.ended || !ctx) return;
                        try {
                            ctx.drawImage(v, 0, 0, 8, 8);
                            var d = ctx.getImageData(0, 0, 8, 8).data;
                            var r = 0, g = 0, b = 0, c = 0;
                            for (var i = 0; i < d.length; i += 16) { r += d[i]; g += d[i+1]; b += d[i+2]; c++; }
                            if (c > 0) {
                                r = Math.round(r / c); g = Math.round(g / c); b = Math.round(b / c);
                                el.style.background = 'radial-gradient(circle at 50% 45%,rgba(' + r + ',' + g + ',' + b + ',0.65),rgba(' + r + ',' + g + ',' + b + ',0.20) 65%,transparent 85%)';
                            }
                        } catch (e) {}
                    }, 500);
                })();
            """.trimIndent(),
        ),
        BrowserExtensionItem(
            id = "night_dimmer",
            title = "Car Night Dimmer",
            subtitle = "Soft warm night overlay for low-glare driving",
            badge = "Night Safe",
            primaryColor = Color(0xFF5E35B1),
            secondaryColor = Color(0xFF311B92),
            jsCode = """
                (function() {
                    if (document.getElementById('gs-ext-night-dim')) return;
                    var ov = document.createElement('div');
                    ov.id = 'gs-ext-night-dim';
                    ov.style.cssText = 'position:fixed;inset:0;z-index:2147483640;pointer-events:none;background:rgba(255,145,40,0.12)';
                    (document.body || document.documentElement).appendChild(ov);
                })();
            """.trimIndent(),
        ),
        BrowserExtensionItem(
            id = "focus_video_clean",
            title = "Distraction & Overlay Clean",
            subtitle = "Hides floating promo banners & clutter on pages",
            badge = "Clean View",
            primaryColor = Color(0xFF00897B),
            secondaryColor = Color(0xFF004D40),
            jsCode = """
                (function() {
                    if (document.getElementById('gs-ext-clean-css')) return;
                    var s = document.createElement('style');
                    s.id = 'gs-ext-clean-css';
                    s.textContent = '[class*="promo"],[class*="cookie-banner"],[id*="cookie-banner"],.ytp-ce-element{display:none !important;}';
                    (document.head || document.documentElement).appendChild(s);
                })();
            """.trimIndent(),
        ),
        BrowserExtensionItem(
            id = "video_boost_125x",
            title = "Smart 1.25x Speed & Boost",
            subtitle = "Smooth slightly faster playback on HTML5 videos",
            badge = "Media",
            primaryColor = Color(0xFFE65100),
            secondaryColor = Color(0xFFBF360C),
            jsCode = """
                (function() {
                    var vids = document.querySelectorAll('video');
                    vids.forEach(function(v) {
                        if (v && !v.__gsSpeedApplied) {
                            v.playbackRate = 1.25;
                            v.__gsSpeedApplied = true;
                        }
                    });
                })();
            """.trimIndent(),
        ),
    )
}

object BrowserExtensionsManager {
    private val installedFlow = MutableStateFlow<Set<String>>(emptySet())
    val installedIds: StateFlow<Set<String>> = installedFlow.asStateFlow()
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.applicationContext
            .getSharedPreferences(EXT_PREFS, Context.MODE_PRIVATE)
            .getString(EXT_KEY, null) ?: return
        val set = runCatching {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i).trim()
                    if (id.isNotEmpty()) add(id)
                }
            }
        }.getOrDefault(emptySet())
        installedFlow.value = set
    }

    private fun persist(context: Context, set: Set<String>) {
        val arr = JSONArray()
        set.forEach { arr.put(it) }
        context.applicationContext
            .getSharedPreferences(EXT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(EXT_KEY, arr.toString())
            .apply()
    }

    fun isInstalled(context: Context, id: String): Boolean {
        ensureLoaded(context)
        return id in installedFlow.value
    }

    fun install(context: Context, id: String) {
        ensureLoaded(context)
        val updated = installedFlow.value + id
        installedFlow.value = updated
        persist(context, updated)
    }

    fun uninstall(context: Context, id: String) {
        ensureLoaded(context)
        val updated = installedFlow.value - id
        installedFlow.value = updated
        persist(context, updated)
    }

    fun combinedJs(context: Context): String {
        ensureLoaded(context)
        val active = installedFlow.value
        val cleanup = """
            (function() {
                var a = document.getElementById('gs-ext-ambient'); if (a && a.parentNode) a.parentNode.removeChild(a);
                var n = document.getElementById('gs-ext-night-dim'); if (n && n.parentNode) n.parentNode.removeChild(n);
                var c = document.getElementById('gs-ext-clean-css'); if (c && c.parentNode) c.parentNode.removeChild(c);
            })();
        """.trimIndent()
        val scripts = BrowserExtensionsCatalog.items
            .filter { it.id in active }
            .joinToString("\n") { ext -> "try { ${ext.jsCode} } catch (e) {}" }
        return "$cleanup\n$scripts"
    }
}