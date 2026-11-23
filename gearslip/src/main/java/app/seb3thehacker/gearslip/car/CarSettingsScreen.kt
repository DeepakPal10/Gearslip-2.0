package app.seb3thehacker.gearslip.car

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.seb3thehacker.gearslip.AppSettings
import app.seb3thehacker.gearslip.R
import app.seb3thehacker.gearslip.car.theme.DarkStyle
import app.seb3thehacker.gearslip.car.theme.GsButton
import app.seb3thehacker.gearslip.car.theme.GsColors
import app.seb3thehacker.gearslip.car.theme.GsIconBox
import app.seb3thehacker.gearslip.car.theme.GsSwitch
import app.seb3thehacker.gearslip.car.theme.GsThemes
import app.seb3thehacker.gearslip.car.theme.GsTone
import app.seb3thehacker.gearslip.car.theme.gsPanelShadow
import app.seb3thehacker.gearslip.car.theme.withTintedSurfaces
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

private const val PREF_CUSTOM_CAR_APPS = "custom_car_apps_csv"

internal fun getSelectedCustomCarApps(context: Context): Set<String> {
    val raw = AppSettings.getString(context, PREF_CUSTOM_CAR_APPS, "")
    if (raw.isBlank()) return emptySet()
    return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

internal fun setSelectedCustomCarApps(context: Context, packages: Set<String>) {
    AppSettings.putString(context, PREF_CUSTOM_CAR_APPS, packages.sorted().joinToString(","))
}

internal fun launchCustomCarApp(context: Context, packageName: String): Boolean {
    return runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

private data class InstalledPhoneApp(
    val packageName: String,
    val label: String,
)

private fun loadLaunchablePhoneApps(context: Context): List<InstalledPhoneApp> {
    val pm = context.packageManager
    val queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val activities = pm.queryIntentActivities(queryIntent, 0)
    return activities
        .mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val title = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
            InstalledPhoneApp(packageName = pkg, label = title)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/**
 * Settings that make sense while sitting in the car. The web page URL is edited here, with the
 * car's own on-screen keyboard ([CarKeyboard]); certificate management still needs a real
 * keyboard and file picker, so it stays on the phone's Settings screen.
 */
@Composable
fun CarSettingsScreen() {
    val scale by CarSettings.scale.collectAsState()
    val open by CarSettings.openOnConnect.collectAsState()
    val launcher by LauncherMode.active.collectAsState()
    val night by CarSettings.nightMode.collectAsState()
    val units by CarSettings.units.collectAsState()
    val navBar by CarSettings.navBarMode.collectAsState()
    val screenSize by CarSettings.screenSize.collectAsState()
    val screenFit by CarSettings.screenFit.collectAsState()
    val whatsNew by CarSettings.whatsNewAfterUpdates.collectAsState()
    val pipeAudio by CarSettings.pipeAudio.collectAsState()
    val speakDirections by CarSettings.speakDirections.collectAsState()
    val directionsOverMusic by CarSettings.directionsOverMusic.collectAsState()
    val bluetoothWarning by CarSettings.bluetoothWarning.collectAsState()
    val locationWarning by CarSettings.locationWarning.collectAsState()
    val autoplay by CarSettings.autoplay.collectAsState()
    val playerTitle by CarSettings.playerTitle.collectAsState()
    val playerLeft by CarSettings.playerLeft.collectAsState()
    val navMic by CarSettings.navMic.collectAsState()
    val theme by CarSettings.appTheme.collectAsState()
    val uiTheme by CarSettings.uiTheme.collectAsState()
    val darkStyle by CarSettings.darkStyle.collectAsState()
    val experimentalFeatures by CarSettings.experimentalFeaturesEnabled.collectAsState()
    val carSensors by CarSettings.carSensorsEnabled.collectAsState()
    val adaptiveVideo by CarSettings.adaptiveVideoEnabled.collectAsState()
    val clusterVideo by CarSettings.clusterVideoEnabled.collectAsState()
    val systemSpeech by CarSettings.systemSpeech.collectAsState()
    val display by CarEnvironment.display.collectAsState()
    val vehicle by CarEnvironment.vehicle.collectAsState()
    val context = LocalContext.current

    var chosen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(chosen) { listState.scrollToItem(0) }
    Row(Modifier.fillMaxSize().padding(start = 16.dp, top = 12.dp)) {
        SettingsRail(
            listOf(
                stringResource(R.string.car_group_screen),
                stringResource(R.string.car_group_nav_bar),
                stringResource(R.string.car_group_sound),
                stringResource(R.string.car_group_warnings),
                stringResource(R.string.car_group_more),
            ),
            chosen,
        ) { chosen = it }
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (chosen) {
                0 -> {
                    item { Group(stringResource(R.string.car_group_screen)) }
                    item {
                        ChoiceRow(
                            stringResource(R.string.car_open_on_connect),
                            listOf(CarSettings.HOME to stringResource(R.string.choice_open_home)) + CarApps.shown(launcher, LauncherMode.canCall(context)).map { it.id to stringResource(it.labelRes) },
                            open,
                        ) { CarSettings.setOpenOnConnect(it) }
                    }
                    item {
                        ChoiceRow(
                            stringResource(R.string.car_size),
                            listOf(1f to stringResource(R.string.choice_size_normal), 1.25f to stringResource(R.string.choice_size_large), 1.5f to stringResource(R.string.choice_size_xlarge)),
                            scale,
                        ) { CarSettings.setScale(it) }
                    }
                    item {
                        ChoiceRow(
                            stringResource(R.string.car_app_theme),
                            listOf(AppTheme.PHONE to stringResource(R.string.choice_theme_phone), AppTheme.LIGHT to stringResource(R.string.choice_theme_light), AppTheme.DARK to stringResource(R.string.choice_theme_dark)),
                            theme,
                        ) { CarSettings.setAppTheme(it) }
                    }
                    if (theme != AppTheme.LIGHT) item {
                        ChoiceRow(
                            stringResource(R.string.car_dark_mode),
                            listOf(
                                DarkStyle.BLACK to stringResource(R.string.choice_dark_black),
                                DarkStyle.BLACK_COLOUR to stringResource(R.string.choice_dark_black_colour),
                                DarkStyle.DEEP_COLOUR to stringResource(R.string.choice_dark_deep_colour),
                                DarkStyle.GREY to stringResource(R.string.choice_dark_grey),
                            ),
                            darkStyle,
                        ) { CarSettings.setDarkStyle(it) }
                    }
                    item {
                        ChoiceRow(
                            stringResource(R.string.car_buttons),
                            GsThemes.builtIn.map { it.id to it.name },
                            uiTheme,
                        ) { CarSettings.setUiTheme(it) }
                    }
                    item {
                        ChoiceRow(
                            stringResource(R.string.car_map_day_night),
                            listOf(NightMode.AUTO to stringResource(R.string.choice_auto), NightMode.DAY to stringResource(R.string.choice_night_day), NightMode.NIGHT to stringResource(R.string.choice_night_night)),
                            night,
                        ) { CarSettings.setNightMode(it) }
                    }
                    item {
                        ChoiceRow(
                            stringResource(R.string.car_units),
                            listOf(Units.AUTO to stringResource(R.string.choice_auto), Units.IMPERIAL to stringResource(R.string.carset_imperial), Units.METRIC to stringResource(R.string.carset_metric)),
                            units,
                        ) { CarSettings.setUnits(it) }
                    }
                    item { SubGroup(stringResource(R.string.carset_advanced)) }
                    item {
                        ChoiceRow(
                            stringResource(R.string.carset_screen_size),
                            listOf<ScreenSize?>(null).plus(SCREEN_PRESETS).plus(screenSize).distinct()
                                .map { it to (it?.toString() ?: stringResource(R.string.screen_from_car)) },
                            screenSize,
                        ) { CarSettings.setScreenSize(it) }
                    }
                    if (screenSize != null) item {
                        ChoiceRow(
                            stringResource(R.string.carset_fit),
                            listOf(ScreenFit.CROP to stringResource(R.string.screen_crop), ScreenFit.SQUASH to stringResource(R.string.screen_squash)),
                            screenFit,
                        ) { CarSettings.setScreenFit(it) }
                    }
                    item { ScreenFit(vehicle) }
                }
                1 -> {
                    item { Group(stringResource(R.string.car_group_nav_bar)) }
                    item {
                        ChoiceRow(
                            stringResource(R.string.car_nav_bar),
                            listOf(
                                NavBarMode.SHOW to stringResource(R.string.choice_nav_show),
                                NavBarMode.HIDE_NAVIGATING to stringResource(R.string.choice_nav_hide_navigating),
                                NavBarMode.HIDE to stringResource(R.string.choice_nav_hide),
                            ),
                            navBar,
                        ) { CarSettings.setNavBarMode(it) }
                    }
                    item {
                        SwitchRow(stringResource(R.string.car_nav_mic), stringResource(R.string.car_nav_mic_note), navMic) { CarSettings.setNavMic(it) }
                    }
                    item {
                        ChoiceRow(
                            stringResource(R.string.car_player_nav_bar),
                            listOf(false to stringResource(R.string.car_skip_buttons), true to stringResource(R.string.car_song_title)),
                            playerTitle,
                        ) { CarSettings.setPlayerTitle(it) }
                    }
                    item {
                        ChoiceRow(
                            stringResource(R.string.car_player_beside_map),
                            listOf(false to stringResource(R.string.car_right), true to stringResource(R.string.car_left)),
                            playerLeft,
                        ) { CarSettings.setPlayerLeft(it) }
                    }
                    item { Note(stringResource(R.string.car_player_beside_map_note)) }
                    item {
                        SwitchRow(stringResource(R.string.car_autoplay), stringResource(R.string.car_autoplay_note), autoplay) { CarSettings.setAutoplay(it) }
                    }
                }
                2 -> {
                    item { Group(stringResource(R.string.car_group_sound)) }
                    item {
                        ChoiceRow(
                            stringResource(R.string.car_audio),
                            listOf(false to stringResource(R.string.guide_audio_bluetooth), true to stringResource(R.string.guide_audio_usb)),
                            pipeAudio,
                        ) { CarSettings.setPipeAudio(it) }
                    }
                    item { Note(stringResource(R.string.carset_audio_note)) }
                    item {
                        SwitchRow(stringResource(R.string.car_spoken_directions), stringResource(R.string.carset_directions_note), speakDirections) { CarSettings.setSpeakDirections(it) }
                    }
                    if (speakDirections) item {
                        SwitchRow(stringResource(R.string.car_directions_over_music), "Play spoken directions over media audio", directionsOverMusic) { CarSettings.setDirectionsOverMusic(it) }
                    }
                    item {
                        SwitchRow(stringResource(R.string.car_phone_speech), stringResource(R.string.carset_phone_speech_note), systemSpeech) { CarSettings.setSystemSpeech(it) }
                    }
                }
                3 -> {
                    item { Group(stringResource(R.string.car_group_warnings)) }
                    if (!pipeAudio) item {
                        SwitchRow(stringResource(R.string.car_warn_bluetooth), null, bluetoothWarning) { CarSettings.setBluetoothWarning(it) }
                    }
                    item {
                        SwitchRow(stringResource(R.string.car_warn_location), null, locationWarning) { CarSettings.setLocationWarning(it) }
                    }
                }
                else -> {
                    item { Group(stringResource(R.string.car_group_more)) }
                    item { CustomCarAppsSetting() }
                    item { WebPageSetting() }
                    item {
                        SwitchRow(stringResource(R.string.car_experimental), stringResource(R.string.car_experimental_note), experimentalFeatures) { CarSettings.setExperimentalFeaturesEnabled(it) }
                    }
                    if (experimentalFeatures) item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SubGroup(stringResource(R.string.carset_experimental_heading))
                            ExperimentalFeaturesList(carSensors, adaptiveVideo, clusterVideo)
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubGroup(stringResource(R.string.settings_connection))
                            val none = stringResource(R.string.status_idle)
                            Info(stringResource(R.string.carset_phone), "${Build.MANUFACTURER} ${Build.MODEL}")
                            Info(stringResource(R.string.carset_vehicle), vehicle.ifEmpty { none })
                            Info(stringResource(R.string.carset_video), display.ifEmpty { none })
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubGroup(stringResource(R.string.settings_about))
                            Info(stringResource(R.string.carset_version), app.seb3thehacker.gearslip.BuildConfig.VERSION_NAME)
                            Info(stringResource(R.string.carset_build), app.seb3thehacker.gearslip.BuildConfig.VERSION_CODE.toString())
                            SwitchRow(
                                stringResource(R.string.carset_whats_new_after),
                                stringResource(R.string.carset_whats_new_after_sub),
                                whatsNew,
                            ) { CarSettings.setWhatsNewAfterUpdates(it) }
                            GsButton(onClick = CarSettings::openWhatsNew, tone = GsTone.Tonal, modifier = Modifier.height(56.dp)) {
                                Text(stringResource(R.string.settings_whats_new), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomCarAppsSetting() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(getSelectedCustomCarApps(context)) }
    val apps = remember(context) { loadLaunchablePhoneApps(context) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Custom Car Apps",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            onClick = { expanded = !expanded },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (selected.isEmpty()) "Select apps to show & run on car screen"
                        else "${selected.size} app(s) enabled for car screen",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (expanded) "Tap to collapse app list" else "Tap to choose any installed app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (expanded) "Hide" else "Choose",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                apps.forEach { appInfo ->
                    val checked = appInfo.packageName in selected
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    appInfo.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    appInfo.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            GsButton(
                                onClick = { launchCustomCarApp(context, appInfo.packageName) },
                                tone = GsTone.Tonal,
                                modifier = Modifier.height(40.dp),
                            ) {
                                Text("Run", style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.width(10.dp))
                            GsSwitch(
                                checked = checked,
                                onCheckedChange = { isOn ->
                                    val updated = if (isOn) selected + appInfo.packageName else selected - appInfo.packageName
                                    selected = updated
                                    setSelectedCustomCarApps(context, updated)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubGroup(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 720.dp && options.size <= 4) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(0.3f))
                Box(Modifier.weight(0.7f)) { ChoiceButtons(options, selected, onSelect) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                ChoiceButtons(options, selected, onSelect)
            }
        }
    }
}

@Composable
private fun SettingsRail(groups: List<String>, chosen: Int, onChoose: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Surface(
        Modifier.width(250.dp).padding(bottom = 12.dp).fillMaxHeight().gsPanelShadow(shape),
        shape = shape,
        color = scheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(10.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 10.dp),
            )
            groups.forEachIndexed { i, name ->
                val on = i == chosen
                GsIconBox(
                    onClick = { onChoose(i) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = if (on) GsColors(scheme.primaryContainer, scheme.onPrimaryContainer)
                    else GsColors(Color.Transparent, scheme.onSurface),
                    latched = on,
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun <T> ChoiceButtons(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            val scheme = MaterialTheme.colorScheme
            GsIconBox(
                onClick = { onSelect(value) },
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                colors = if (on) GsColors(scheme.primaryContainer, scheme.onPrimaryContainer)
                else GsColors(scheme.surfaceContainerHigh, scheme.onSurface),
                latched = on,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ExperimentalFeaturesList(carSensors: Boolean, adaptiveVideo: Boolean, clusterVideo: Boolean) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SwitchRow(
            title = stringResource(R.string.car_vehicle_data),
            description = stringResource(R.string.carset_vehicle_data_note),
            checked = carSensors,
        ) { CarSettings.setCarSensorsEnabled(it) }
        SwitchRow(
            title = stringResource(R.string.car_adaptive_video),
            description = stringResource(R.string.carset_adaptive_note),
            checked = adaptiveVideo,
        ) { CarSettings.setAdaptiveVideoEnabled(it) }
        SwitchRow(
            title = stringResource(R.string.car_cluster_video),
            description = "Show video on instrument cluster display when supported",
            checked = clusterVideo,
        ) { CarSettings.setClusterVideoEnabled(it) }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    warning: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (description != null) Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            GsSwitch(checked, onCheckedChange)
        }
    }
}

@Composable
private fun WebPageSetting() {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(AppSettings.startupUrl(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.web_page), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Surface(
            onClick = { editing = !editing },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                url.ifEmpty { stringResource(R.string.carset_web_default) },
                style = MaterialTheme.typography.bodyLarge,
                color = if (url.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        Text(
            stringResource(R.string.carset_web_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (editing) {
            CarKeyboard(
                url,
                onTextChange = {
                    url = it
                    AppSettings.setStartupUrl(context, it)
                },
                onSubmit = { editing = false },
                onDismiss = { editing = false },
            )
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ScreenFit(vehicle: String) {
    val insets by CarEnvironment.insets.collectAsState()
    val context = LocalContext.current.applicationContext
    var saved by remember { mutableStateOf<String?>(null) }
    val known = vehicle.isNotEmpty() && vehicle != "Unknown vehicle"

    fun change(new: app.seb3thehacker.gearslip.Insets) {
        saved = null
        CarEnvironment.setInsets(new)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.carset_screen_fit), style = MaterialTheme.typography.titleMedium)
        Stepper(stringResource(R.string.carset_top), insets.top) { change(insets.copy(top = (insets.top + it).coerceAtLeast(0))) }
        Stepper(stringResource(R.string.carset_bottom), insets.bottom) { change(insets.copy(bottom = (insets.bottom + it).coerceAtLeast(0))) }
        Text(
            stringResource(R.string.carset_usable_height, 480 - insets.top - insets.bottom),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (known) GsButton(
            onClick = {
                val ok = app.seb3thehacker.gearslip.VehicleProfiles.saveInsets(context, vehicle, insets)
                saved = if (ok) context.getString(R.string.carset_saved_to, vehicle) else context.getString(R.string.carset_save_failed)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(R.string.carset_save_vehicle)) }
        else Note(stringResource(R.string.carset_fit_needs_car))
        saved?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1.1f))
        StepButton("−5", Modifier.weight(1f)) { onStep(-5) }
        StepButton("−1", Modifier.weight(1f)) { onStep(-1) }
        Text(
            stringResource(R.string.carset_px, value),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1.3f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepButton("+1", Modifier.weight(1f)) { onStep(1) }
        StepButton("+5", Modifier.weight(1f)) { onStep(5) }
    }
}

@Composable
private fun StepButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    GsIconBox(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        colors = GsColors(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

private val SCREEN_PRESETS = listOf(
    ScreenSize(800, 480), ScreenSize(1024, 600), ScreenSize(1280, 480), ScreenSize(1280, 720),
    ScreenSize(1440, 540), ScreenSize(1920, 720), ScreenSize(1920, 1080),
)