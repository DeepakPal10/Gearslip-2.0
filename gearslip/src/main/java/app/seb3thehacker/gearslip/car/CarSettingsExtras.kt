package app.seb3thehacker.gearslip.car

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val CAR_CUSTOM_PREFS = "gearslip_car_custom_prefs"

data class CarCustomConfig(
    val brightness: Float = 0.75f,
    val autoDarkNight: Boolean = true,
    val desktopBrowserDefault: Boolean = false,
    val autoHideFullscreenBar: Boolean = true,
    val autoOpenLastApp: Boolean = false,
    val largeTouchTiles: Boolean = true,
)

object CarCustomPrefs {
    fun load(context: Context): CarCustomConfig {
        val prefs = context.applicationContext.getSharedPreferences(CAR_CUSTOM_PREFS, Context.MODE_PRIVATE)
        return CarCustomConfig(
            brightness = prefs.getFloat("brightness", 0.75f).coerceIn(0.1f, 1.0f),
            autoDarkNight = prefs.getBoolean("auto_dark_night", true),
            desktopBrowserDefault = prefs.getBoolean("desktop_browser_default", false),
            autoHideFullscreenBar = prefs.getBoolean("auto_hide_fullscreen_bar", true),
            autoOpenLastApp = prefs.getBoolean("auto_open_last_app", false),
            largeTouchTiles = prefs.getBoolean("large_touch_tiles", true),
        )
    }

    fun save(context: Context, config: CarCustomConfig) {
        context.applicationContext.getSharedPreferences(CAR_CUSTOM_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat("brightness", config.brightness.coerceIn(0.1f, 1.0f))
            .putBoolean("auto_dark_night", config.autoDarkNight)
            .putBoolean("desktop_browser_default", config.desktopBrowserDefault)
            .putBoolean("auto_hide_fullscreen_bar", config.autoHideFullscreenBar)
            .putBoolean("auto_open_last_app", config.autoOpenLastApp)
            .putBoolean("large_touch_tiles", config.largeTouchTiles)
            .apply()
    }
}

@Composable
fun CustomizableCarSettingsSection(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(CarCustomPrefs.load(context)) }
    var brightnessSlider by remember { mutableFloatStateOf(config.brightness) }

    fun update(block: (CarCustomConfig) -> CarCustomConfig) {
        val next = block(config)
        config = next
        CarCustomPrefs.save(context, next)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Car Display & Customization",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        // Screen Brightness Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Screen Brightness", fontWeight = FontWeight.SemiBold)
                }
                Text("${(brightnessSlider * 100).toInt()}%", fontWeight = FontWeight.Bold)
            }
            Slider(
                value = brightnessSlider,
                onValueChange = { brightnessSlider = it.coerceIn(0.1f, 1.0f) },
                onValueChangeFinished = { update { it.copy(brightness = brightnessSlider) } },
                valueRange = 0.1f..1.0f,
            )
        }

        CustomSwitchRow(
            icon = Icons.Filled.Lock,
            title = "Night / Dark Mode Sync",
            subtitle = "Adjust screen contrast for low-light driving",
            checked = config.autoDarkNight,
            onCheckedChange = { checked -> update { it.copy(autoDarkNight = checked) } },
        )

        CustomSwitchRow(
            icon = Icons.Filled.Search,
            title = "Desktop Mode by Default",
            subtitle = "Open web pages with desktop layout in browser",
            checked = config.desktopBrowserDefault,
            onCheckedChange = { checked -> update { it.copy(desktopBrowserDefault = checked) } },
        )

        CustomSwitchRow(
            icon = Icons.Filled.Settings,
            title = "Auto-Hide Fullscreen Controls",
            subtitle = "Keep video and web fullscreen clean until swipe",
            checked = config.autoHideFullscreenBar,
            onCheckedChange = { checked -> update { it.copy(autoHideFullscreenBar = checked) } },
        )

        CustomSwitchRow(
            icon = Icons.Filled.Home,
            title = "Large Car Touch Targets",
            subtitle = "Bigger buttons and cards for safer in-car tapping",
            checked = config.largeTouchTiles,
            onCheckedChange = { checked -> update { it.copy(largeTouchTiles = checked) } },
        )

        CustomSwitchRow(
            icon = Icons.Filled.Refresh,
            title = "Auto-Restore Last Session",
            subtitle = "Remember last active car app state on launch",
            checked = config.autoOpenLastApp,
            onCheckedChange = { checked -> update { it.copy(autoOpenLastApp = checked) } },
        )

        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable {
                    val defaultCfg = CarCustomConfig()
                    brightnessSlider = defaultCfg.brightness
                    config = defaultCfg
                    CarCustomPrefs.save(context, defaultCfg)
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Reset Custom Car Settings",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun CustomSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }