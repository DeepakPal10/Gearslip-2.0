package app.seb3thehacker.gearslip.car

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.seb3thehacker.gearslip.ProjectedContent
import app.seb3thehacker.gearslip.R

/** One tile in the launcher and the screen it opens. */
class CarApp(
    val id: String,
    /** English, for matching what a driver says; [labelRes] is what the screen shows. */
    val label: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)

internal fun builtInAccentColor(idOrLabel: String): Color = when (idOrLabel.lowercase()) {
    "web", "browser" -> Color(0xFF0091EA)
    "phone", "screen sharing", "screensharing" -> Color(0xFF7C4DFF)
    "dialer", "phone call" -> Color(0xFF00C853)
    "maps", "google maps", "map" -> Color(0xFF1A73E8)
    "settings" -> Color(0xFF455A64)
    "allapps", "all apps" -> Color(0xFF2962FF)
    "exit" -> Color(0xFFFF3B30)
    else -> Color(0xFF007AFF)
}

@Composable
internal fun ColorfulBuiltInIcon(
    idOrLabel: String,
    icon: ImageVector,
    contentDescription: String?,
    boxSize: Dp,
    iconSize: Dp,
    cornerRadius: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    val base = builtInAccentColor(idOrLabel)
    val lighter = base.copy(
        red = (base.red + 0.14f).coerceAtMost(1f),
        green = (base.green + 0.14f).coerceAtMost(1f),
        blue = (base.blue + 0.14f).coerceAtMost(1f),
    )
    Box(
        modifier = modifier
            .size(boxSize)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.verticalGradient(listOf(lighter, base))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Built-in apps including Google Maps launcher/browser fallback. */
object CarApps {

    val all: List<CarApp> = listOf(
        CarApp("maps", "Maps", R.string.crumb_home, Icons.Filled.Place) {
            GoogleMapsScreen()
        },
        CarApp("web", "Browser", R.string.app_browser, Icons.Filled.Search) {
            WebApp(content = ProjectedContent.resolve(LocalContext.current))
        },
        CarApp("phone", "Screen sharing", R.string.app_screen_sharing, Icons.Filled.Share) {
            PhoneAppsScreen()
        },
        CarApp("dialer", "Phone", R.string.carset_phone, Icons.Filled.Call) {
            PhoneDialerScreen()
        },
        CarApp("settings", "Settings", R.string.settings_title, Icons.Filled.Settings) {
            CarSettingsScreen()
        },
        CarApp("allapps", "All apps", R.string.app_all_apps, Icons.Filled.List) {
            AllAppsScreen()
        },
        CarApp("exit", "Exit", R.string.app_browser, Icons.Filled.Close) {
            AllAppsScreen()
        },
    )

    fun find(id: String): CarApp? = all.firstOrNull { it.id == id }

    /** The screens [BuiltInApps.entries] offers here, so a choice list never names a hidden one. */
    fun shown(launcher: Boolean, canCall: Boolean): List<CarApp> {
        val ids = BuiltInApps.entries(launcher, canCall).mapNotNull { (it.builtIn?.screen as? CarScreen.App)?.id }.toSet()
        return all.filter { it.id in ids }
    }
}

@Composable
internal fun GoogleMapsScreen() {
    val context = LocalContext.current
    val navigator = LocalCarNavigator.current
    val frame = CarEnvironment.frame.value

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val launchedNav = runCatching {
            val navApps = app.seb3thehacker.gearslip.host.CarAppCatalog.installed(context)
            val mapsApp = navApps.firstOrNull {
                it.component.packageName.contains("maps", ignoreCase = true) || it.isNavigation
            }
            if (mapsApp != null) {
                CarServices.connectNav(mapsApp, frame)
                navigator.home()
                true
            } else {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                if (launchIntent != null) {
                    launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    true
                } else {
                    false
                }
            }
        }.getOrDefault(false)

        if (!launchedNav) {
            navigator.home()
        }
    }

    WebApp(content = "https://maps.google.com")
}