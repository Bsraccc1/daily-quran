package com.quranreader.custom.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quranreader.custom.ui.screens.bookmarks.BookmarksScreen
import com.quranreader.custom.ui.screens.downloads.ManageDownloadsScreen
import com.quranreader.custom.ui.screens.home.DashboardScreen
import com.quranreader.custom.ui.screens.juz.JuzScreen
import com.quranreader.custom.ui.screens.memorization.MemorizationHistoryScreen
import com.quranreader.custom.ui.screens.reading.MushafReaderScreen
import com.quranreader.custom.ui.screens.session.SessionManagementScreen
import com.quranreader.custom.ui.screens.settings.SettingsScreen
import com.quranreader.custom.ui.viewmodel.SettingsViewModel

// ── Route definitions ────────────────────────────────────────────────────────

sealed class Screen(val route: String) {
    object Reading : Screen("reading")
    // page is the mandatory path arg; surah+ayah are optional query args
    // used by the "search by surah and ayah" flow so we land on the page
    // already highlighting the requested verse. Both default to 0 which
    // means "no pre-highlight" so all existing call sites keep working.
    object MushafReader : Screen("mushaf/{page}?surah={surah}&ayah={ayah}") {
        fun createRoute(page: Int, surah: Int = 0, ayah: Int = 0) =
            "mushaf/$page?surah=$surah&ayah=$ayah"
    }
    /**
     * Mushaf reader with auto-session start. The session metadata
     * (start page + target pages) is passed explicitly through the
     * route arguments instead of read from DataStore so the
     * `LaunchedEffect` that calls `startSessionWithStart(...)` on the
     * reader doesn't race the legacy session Flow's first emission.
     * Without this, opening from a multi-session card would default
     * `targetPages` to the user's `newSessionLimit` setting (typically
     * 5) regardless of what the session actually requested.
     */
    object MushafReaderWithSession : Screen("mushaf_session/{page}?startPage={startPage}&targetPages={targetPages}") {
        fun createRoute(page: Int, startPage: Int = page, targetPages: Int = 0) =
            "mushaf_session/$page?startPage=$startPage&targetPages=$targetPages"
    }
    object Juz : Screen("juz")
    object Sessions : Screen("sessions")
    object Bookmarks : Screen("bookmarks")
    object Settings : Screen("settings")
    object ManageDownloads : Screen("manage_downloads") // audio downloads
    object MemorizationHistory : Screen("memorization_history")
}

// ── Bottom navigation tab definitions ────────────────────────────────────────

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Reading.route, "Reading", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    BottomNavItem(Screen.Juz.route, "Juz", Icons.Filled.GridView, Icons.Outlined.GridView),
    BottomNavItem(Screen.Sessions.route, "Session", Icons.Filled.Timer, Icons.Outlined.Timer),
    BottomNavItem(Screen.Bookmarks.route, "Bookmark", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
    BottomNavItem(Screen.Settings.route, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

// Routes where the bottom bar should be visible
private val bottomBarRoutes = bottomNavItems.map { it.route }.toSet()

// ── Main Navigation Graph ────────────────────────────────────────────────────

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun QuranNavGraph(
    navController: NavHostController = rememberNavController()
) {
    @Suppress("UNUSED_VARIABLE")
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    // v4.1: every page is bundled as an asset (assets/pages_madinah/page_NNN.jpg),
    // so there is no first-launch download flow. Boot straight into Reading.
    val startDestination = Screen.Reading.route

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show bottom bar only on the 5 main tabs
    val showBottomBar = currentRoute in bottomBarRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AnimatedBottomNavigationBar(
                    items = bottomNavItems,
                    currentRoute = currentRoute,
                    onItemClick = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(150, easing = LinearOutSlowInEasing))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(100, easing = FastOutLinearInEasing))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(150, easing = LinearOutSlowInEasing))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(100, easing = FastOutLinearInEasing))
            }
        ) {
            // ── Tab 1: Reading (Dashboard - With Session Controls) ───────────
            composable(Screen.Reading.route) {
                DashboardScreen(
                    // Search now lives in a dialog hosted inside
                    // DashboardScreen — this callback fires only after
                    // the user has resolved a verse, jumping straight
                    // to the reader pre-highlighted.
                    onNavigateToAyah = { page, surah, ayah ->
                        navController.navigate(
                            Screen.MushafReader.createRoute(page, surah, ayah)
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToMushafWithSession = { page, startPage, targetPages ->
                        navController.navigate(
                            Screen.MushafReaderWithSession.createRoute(
                                page = page,
                                startPage = startPage,
                                targetPages = targetPages,
                            )
                        ) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // ── Tab 2: Juz ────────────────────────────────────────────────────
            composable(Screen.Juz.route) {
                JuzScreen(
                    onNavigateToReading = { page ->
                        // Navigate directly to Mushaf Reader
                        navController.navigate(Screen.MushafReader.createRoute(page)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // ── Tab 3: Session ────────────────────────────────────────────────
            composable(Screen.Sessions.route) {
                SessionManagementScreen(
                    onStartReading = { page, startPage, targetPages ->
                        // Navigate to Mushaf Reader WITH session auto-start.
                        // We forward `startPage` and `targetPages` explicitly
                        // so the reader's auto-session uses the *session's*
                        // start + target instead of racing the legacy
                        // DataStore Flow's first emission.
                        navController.navigate(
                            Screen.MushafReaderWithSession.createRoute(
                                page = page,
                                startPage = startPage,
                                targetPages = targetPages,
                            )
                        ) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // ── Tab 4: Bookmark ───────────────────────────────────────────────
            composable(Screen.Bookmarks.route) {
                BookmarksScreen(
                    onNavigateToReading = { page ->
                        // Navigate directly to Mushaf Reader
                        navController.navigate(Screen.MushafReader.createRoute(page)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // ── Tab 5: Settings ───────────────────────────────────────────────
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToManageDownloads = {
                        navController.navigate(Screen.ManageDownloads.route)
                    },
                    onNavigateToMemorizationHistory = {
                        navController.navigate(Screen.MemorizationHistory.route)
                    }
                )
            }

            // ── v3.0 Secondary: Manage Audio Downloads ───────────────────────
            composable(Screen.ManageDownloads.route) {
                ManageDownloadsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onBrowseSurahs = {
                        navController.navigate(Screen.Juz.route) {
                            popUpTo(Screen.Settings.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // ── v3.0 Secondary: Memorization History ─────────────────────────
            composable(Screen.MemorizationHistory.route) {
                MemorizationHistoryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onResume = { ayah, _, _ ->
                        // Navigate to mushaf at the surah's start page
                        val page = com.quranreader.custom.data.QuranInfo.getStartPage(ayah.surah)
                        navController.navigate(Screen.MushafReader.createRoute(page)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // ── Secondary: Mushaf Reader (Layer 2) ───────────────────────────
            composable(
                route = Screen.MushafReader.route,
                arguments = listOf(
                    navArgument("page") {
                        type = NavType.IntType
                        defaultValue = 1
                    },
                    navArgument("surah") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("ayah") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { backStackEntry ->
                val args = backStackEntry.arguments
                val page = args?.getInt("page") ?: 1
                val surah = args?.getInt("surah") ?: 0
                val ayah = args?.getInt("ayah") ?: 0
                MushafReaderScreen(
                    initialPage = page,
                    onBack = { navController.popBackStack() },
                    startSessionAutomatically = false,
                    initialHighlightSurah = surah,
                    initialHighlightAyah = ayah,
                )
            }

            // ── Secondary: Mushaf Reader WITH Session (from Session tab) ─────
            composable(
                route = Screen.MushafReaderWithSession.route,
                arguments = listOf(
                    navArgument("page") {
                        type = NavType.IntType
                        defaultValue = 1
                    },
                    navArgument("startPage") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("targetPages") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { backStackEntry ->
                val page = backStackEntry.arguments?.getInt("page") ?: 1
                // 0 means "not provided" — the reader falls back to the
                // page itself for startPage and to the user's
                // newSessionLimit setting for targetPages. This keeps
                // existing call sites that still construct the legacy
                // `mushaf_session/{page}` URL working without surprise.
                val startPage = backStackEntry.arguments?.getInt("startPage")?.takeIf { it > 0 } ?: page
                val targetPages = backStackEntry.arguments?.getInt("targetPages") ?: 0
                MushafReaderScreen(
                    initialPage = page,
                    onBack = { navController.popBackStack() },
                    startSessionAutomatically = true,
                    sessionStartPageOverride = startPage,
                    sessionTargetPagesOverride = targetPages,
                )
            }

            // Search is no longer a destination in the nav graph — it
            // lives as a popup dialog hosted inside DashboardScreen so
            // there's no fullscreen route to register here.
        }
    }
}

// ── Animated Bottom Navigation Bar ──────────────────────────────────────────

@Composable
private fun AnimatedBottomNavigationBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onItemClick: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                
                AnimatedNavigationItem(
                    item = item,
                    selected = selected,
                    onClick = { onItemClick(item.route) },
                    modifier = Modifier.weight(1f) // Equal weight for all items
                )
            }
        }
    }
}

@Composable
private fun AnimatedNavigationItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Smooth alpha animation without scale to prevent clipping
    val animatedAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.7f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "alpha"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            Color.Transparent,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "background"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (selected) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "content"
    )

    Surface(
        onClick = onClick,
        modifier = modifier // Apply the passed modifier (weight)
            .padding(4.dp)
            .alpha(animatedAlpha),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp) // Reduced horizontal padding
                .widthIn(min = 48.dp), // Reduced minimum width
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier.size(22.dp) // Slightly smaller icon
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // Always show label but animate opacity for smooth transition
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = if (selected) 1f else 0f),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .animateContentSize(
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    )
            )
        }
    }
}
