package app.seb3thehacker.gearslip.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seb3thehacker.gearslip.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarSettingsScreen() {
    val currentTheme by CarSettings.appTheme.collectAsStateWithLifecycle()
    val currentNightMode by CarSettings.nightMode.collectAsStateWithLifecycle()

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

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Choose how the car interface colors behave.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppTheme.entries.forEach { theme ->
                        val label = when (theme) {
                            AppTheme.PHONE -> "Phone"
                            AppTheme.LIGHT -> "Light"
                            AppTheme.DARK -> "Dark"
                        }
                        FilterChip(
                            selected = currentTheme == theme,
                            onClick = { CarSettings.setAppTheme(theme) },
                            label = { Text(label) },
                        )
                    }
                }

                Text(
                    text = "Day / Night Mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NightMode.entries.forEach { mode ->
                        val label = when (mode) {
                            NightMode.AUTO -> "Auto"
                            NightMode.DAY -> "Day"
                            NightMode.NIGHT -> "Night"
                        }
                        FilterChip(
                            selected = currentNightMode == mode,
                            onClick = { CarSettings.setNightMode(mode) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CustomizableCarSettingsSection(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp),
            )
        }
    }
}