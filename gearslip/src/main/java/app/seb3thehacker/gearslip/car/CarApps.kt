package app.seb3thehacker.gearslip.car

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

data class HubCatalogItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val rating: String,
    val sizeTag: String,
    val url: String,
    val icon: ImageVector,
    val primaryColor: Color,
    val secondaryColor: Color,
)

internal fun builtInAccentColor(idOrLabel: String): Color = when (idOrLabel.lowercase()) {
    "web", "browser" -> Color(0xFF0091EA)
    "phone", "screen sharing", "screensharing" -> Color(0xFF7C4DFF)
    "dialer", "phone call" -> Color(0xFF00C853)
    "maps", "google maps", "map" -> Color(0xFF1A73E8)
    "settings" -> Color(0xFF455A64)
    "allapps", "all apps", "app hub" -> Color(0xFF00BFA5)
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
        red = (base.red + 0.16f).coerceAtMost(1f),
        green = (base.green + 0.16f).coerceAtMost(1f),
        blue = (base.blue + 0.16f).coerceAtMost(1f),
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

private val HUB_CATALOG_ITEMS = listOf(
    // Streaming Platforms
    HubCatalogItem("yt", "YouTube", "Videos & Live Streams", "Streaming", "4.8", "Web App", "https://m.youtube.com", Icons.Filled.PlayArrow, Color(0xFFFF1744), Color(0xFFB71C1C)),
    HubCatalogItem("netflix", "Netflix", "Movies & Web Series", "Streaming", "4.7", "HD Stream", "https://www.netflix.com", Icons.Filled.Movie, Color(0xFFE50914), Color(0xFF8E0000)),
    HubCatalogItem("prime", "Prime Video", "Originals & Live Sports", "Streaming", "4.6", "HD Stream", "https://www.primevideo.com", Icons.Filled.LiveTv, Color(0xFF00A8E1), Color(0xFF005B9F)),
    HubCatalogItem("hotstar", "Disney+ Hotstar", "Cricket, Movies & Shows", "Streaming", "4.7", "Live TV", "https://www.hotstar.com", Icons.Filled.Star, Color(0xFF1565C0), Color(0xFF0D47A1)),
    HubCatalogItem("jiocinema", "JioCinema", "IPL, Movies & Reality", "Streaming", "4.5", "Free HD", "https://www.jiocinema.com", Icons.Filled.Theaters, Color(0xFFD81B60), Color(0xFF880E4F)),
    HubCatalogItem("sonyliv", "SonyLIV", "Sports & Originals", "Streaming", "4.4", "Stream", "https://www.sonyliv.com", Icons.Filled.Tv, Color(0xFF6A1B9A), Color(0xFF38006B)),
    HubCatalogItem("zee5", "ZEE5", "Hindi & Regional Shows", "Streaming", "4.3", "Stream", "https://www.zee5.com", Icons.Filled.LiveTv, Color(0xFF512DA8), Color(0xFF311B92)),
    HubCatalogItem("twitch", "Twitch", "Live Gaming Streams", "Streaming", "4.6", "Live", "https://m.twitch.tv", Icons.Filled.VideogameAsset, Color(0xFF9146FF), Color(0xFF5E35B1)),

    // Games
    HubCatalogItem("minecraft", "Minecraft", "Sandbox Survival & SMP", "Games", "4.9", "Cloud / Web", "https://www.minecraft.net", Icons.Filled.Extension, Color(0xFF43A047), Color(0xFF1B5E20)),
    HubCatalogItem("forza", "Forza Horizon", "Open-World Car Racing", "Games", "4.8", "Cloud Play", "https://www.xbox.com/play", Icons.Filled.DirectionsCar, Color(0xFFFF6D00), Color(0xFFE65100)),
    HubCatalogItem("palworld", "Palworld", "Open-World Monster Survival", "Games", "4.7", "Cloud Play", "https://www.pocketpair.jp/palworld", Icons.Filled.Explore, Color(0xFF00ACC1), Color(0xFF006064)),
    HubCatalogItem("greenhell", "Green Hell", "Amazon Rainforest Survival", "Games", "4.6", "Survival", "https://www.greenhell-game.com", Icons.Filled.LocalFireDepartment, Color(0xFF689F38), Color(0xFF33691E)),
    HubCatalogItem("asphalt", "Asphalt Legends", "Arcade Street Racing", "Games", "4.6", "Racing", "https://www.gameloft.com", Icons.Filled.Speed, Color(0xFF2979FF), Color(0xFF1565C0)),
    HubCatalogItem("poki", "Poki Car Arcade", "Instant Browser Mini-Games", "Games", "4.7", "Instant", "https://poki.com", Icons.Filled.SportsEsports, Color(0xFF00BFA5), Color(0xFF00695C)),
    HubCatalogItem("chess", "Chess.com", "Online Chess & Puzzles", "Games", "4.8", "Board", "https://www.chess.com", Icons.Filled.Gamepad, Color(0xFF5D4037), Color(0xFF3E2723)),
    HubCatalogItem("geforcenow", "GeForce NOW", "PC Cloud Gaming Hub", "Games", "4.7", "Cloud", "https://play.geforcenow.com", Icons.Filled.Cloud, Color(0xFF76B900), Color(0xFF33691E)),

    // Music & Radio
    HubCatalogItem("ytmusic", "YouTube Music", "Songs, Mixes & Purane Gane", "Music", "4.8", "Audio", "https://music.youtube.com", Icons.Filled.MusicNote, Color(0xFFFF3D00), Color(0xFFBF360C)),
    HubCatalogItem("spotify", "Spotify", "Music & Podcasts", "Music", "4.8", "Audio", "https://open.spotify.com", Icons.Filled.Album, Color(0xFF1DB954), Color(0xFF1B5E20)),
    HubCatalogItem("jiosaavn", "JioSaavn", "Bollywood & Hindi Radio", "Music", "4.6", "Audio", "https://www.jiosaavn.com", Icons.Filled.Headphones, Color(0xFF26A69A), Color(0xFF004D40)),
    HubCatalogItem("radio", "Radio Garden", "Live FM Stations Worldwide", "Music", "4.7", "FM Live", "https://radio.garden", Icons.Filled.Radio, Color(0xFF00838F), Color(0xFF004D40)),
)

@Composable
internal fun AppHubStoreScreen() {
    val context = LocalContext.current
    val navigator = LocalCarNavigator.current
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var installedIds by remember {
        mutableStateOf(
            PinnedWebSites.sites.value
                .mapNotNull { site -> HUB_CATALOG_ITEMS.firstOrNull { it.url == site.url }?.id }
                .toSet(),
        )
    }

    val categories = listOf("All", "Games", "Streaming", "Music")
    val visibleItems = remember(selectedCategory, searchQuery) {
        val q = searchQuery.trim().lowercase()
        HUB_CATALOG_ITEMS.filter { item ->
            val catOk = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
            val queryOk = q.isEmpty() ||
                item.title.lowercase().contains(q) ||
                item.subtitle.lowercase().contains(q) ||
                item.category.lowercase().contains(q)
            catOk && queryOk
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Play Store Top Header + Search Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ColorfulBuiltInIcon(
                idOrLabel = "allapps",
                icon = Icons.Filled.ShoppingBag,
                contentDescription = "App Hub",
                boxSize = 42.dp,
                iconSize = 22.dp,
                cornerRadius = 12.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Gearslip Play Store",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = "${HUB_CATALOG_ITEMS.size} Apps, Games & Streaming",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Compact Search Input
            Row(
                modifier = Modifier
                    .width(210.dp)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search apps & games",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { searchQuery = "" },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Play Store Category Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { cat ->
                val isSelected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = cat,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Featured Banner (Play Store Spotlight)
        if (selectedCategory == "All" && searchQuery.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0D47A1), Color(0xFF00838F), Color(0xFF00BFA5)),
                        ),
                    )
                    .clickable {
                        installedIds = installedIds + "yt"
                        PinnedWebSites.pin(context, "YouTube", "https://m.youtube.com")
                        PinnedWebSites.openInBrowser(navigator, "https://m.youtube.com")
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FEATURED FOR CAR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Text(
                        text = "YouTube, Netflix, Minecraft & Cloud Games",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "Launch YouTube",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D47A1),
                    )
                }
            }
        }

        // App Store Cards List
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = visibleItems, key = { it.id }) { item ->
                val isInstalled = item.id in installedIds

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable {
                            installedIds = installedIds + item.id
                            PinnedWebSites.pin(context, item.title, item.url)
                            PinnedWebSites.openInBrowser(navigator, item.url)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Unique Gradient Icon per App
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.verticalGradient(listOf(item.primaryColor, item.secondaryColor))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    // Title + Subtitle + Rating Row
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "★ ${item.rating}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "• ${item.category}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "• ${item.sizeTag}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Install -> Uninstall Button
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isInstalled) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.primary,
                            )
                            .clickable {
                                if (isInstalled) {
                                    installedIds = installedIds - item.id
                                    PinnedWebSites.unpin(context, item.url)
                                } else {
                                    installedIds = installedIds + item.id
                                    PinnedWebSites.pin(context, item.title, item.url)
                                    PinnedWebSites.openInBrowser(navigator, item.url)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = if (isInstalled) "Uninstall" else "Install",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isInstalled) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
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
        CarApp("allapps", "App Hub", R.string.app_all_apps, Icons.Filled.ShoppingBag) {
            AppHubStoreScreen()
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