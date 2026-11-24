package app.seb3thehacker.gearslip.car

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.seb3thehacker.gearslip.AppSettings
import app.seb3thehacker.gearslip.R
import app.seb3thehacker.gearslip.car.theme.GsButton
import app.seb3thehacker.gearslip.car.theme.GsSwitch
import app.seb3thehacker.gearslip.car.theme.GsTone

private const val KEY_CUSTOM_CAR_APPS = "car_custom_apps"

internal data class LaunchableAppInfo(
    val label: String,
    val packageName: String,
)

internal fun getSelectedCustomCarApps(context: Context): Set<String> =
    AppSettings.getString(context.applicationContext, KEY_CUSTOM_CAR_APPS, "")
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

internal fun setSelectedCustomCarApps(context: Context, packages: Set<String>) {
    AppSettings.putString(
        context.applicationContext,
        KEY_CUSTOM_CAR_APPS,
        packages.distinct().joinToString(","),
    )
}

internal fun loadLaunchablePhoneApps(context: Context): List<LaunchableAppInfo> {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
    return resolveInfos
        .mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString().trim() }
                .getOrDefault(pkg)
                .ifEmpty { pkg }
            LaunchableAppInfo(label = label, packageName = pkg)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

internal fun launchCustomCarApp(context: Context, packageName: String): Boolean {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(launchIntent)
    return true
}

@Composable
fun CarSettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            modifier = Modifier.fillMaxWidth(),
        )
        CustomCarAppsSetting()
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
                                    warmLauncherApps(context)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}