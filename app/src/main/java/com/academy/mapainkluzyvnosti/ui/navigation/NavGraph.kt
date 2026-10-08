package com.academy.mapainkluzyvnosti.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.academy.mapainkluzyvnosti.ui.components.BottomNavEntry
import com.academy.mapainkluzyvnosti.ui.components.MapaBottomNavBar
import com.academy.mapainkluzyvnosti.ui.screens.categories.CategoriesScreen
import com.academy.mapainkluzyvnosti.ui.screens.favorites.FavoritesScreen
import com.academy.mapainkluzyvnosti.ui.screens.filters.FiltersScreen
import com.academy.mapainkluzyvnosti.ui.screens.login.LoginScreen
import com.academy.mapainkluzyvnosti.ui.screens.map.MapScreen
import com.academy.mapainkluzyvnosti.ui.screens.notifications.NotificationsScreen
import com.academy.mapainkluzyvnosti.ui.screens.photoupload.PhotoUploadScreen
import com.academy.mapainkluzyvnosti.ui.screens.placedetail.PlaceDetailScreen
import com.academy.mapainkluzyvnosti.ui.screens.profile.ProfileScreen
import com.academy.mapainkluzyvnosti.ui.screens.quickcheck.QuickCheckScreen
import com.academy.mapainkluzyvnosti.ui.screens.route.RouteScreen
import com.academy.mapainkluzyvnosti.ui.screens.search.SearchScreen
import com.academy.mapainkluzyvnosti.ui.screens.settings.SettingsScreen
import com.academy.mapainkluzyvnosti.ui.screens.sos.SosRequestScreen
import com.academy.mapainkluzyvnosti.ui.screens.welcome.WelcomeScreen
import com.academy.mapainkluzyvnosti.ui.state.AppStartup
import org.koin.compose.koinInject

@Composable
fun MapaNavHost(startRoute: String) {
    val navController = rememberNavController()
    val startup = koinInject<AppStartup>()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.bottomNavRoutes

    fun goToLogin() = navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                fun selected(route: String) = backStackEntry?.destination?.hierarchy?.any { it.route == route } == true
                MapaBottomNavBar(
                    entries = listOf(
                        BottomNavEntry("Мапа", Icons.Filled.Map, selected(Routes.MAP)) {
                            navController.switchTab(Routes.MAP)
                        },
                        BottomNavEntry("Категорії", Icons.Filled.GridView, selected(Routes.CATEGORIES)) {
                            navController.switchTab(Routes.CATEGORIES)
                        },
                        BottomNavEntry("Обране", Icons.Filled.Favorite, selected(Routes.FAVORITES)) {
                            navController.switchTab(Routes.favorites())
                        },
                        BottomNavEntry("Профіль", Icons.Filled.Person, selected(Routes.PROFILE)) {
                            navController.switchTab(Routes.PROFILE)
                        }
                    )
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.WELCOME) {
                val openLogin = {
                    startup.markOnboardingSeen()
                    navController.navigate(Routes.LOGIN) { popUpTo(Routes.WELCOME) { inclusive = true } }
                }
                WelcomeScreen(onStart = openLogin, onSignIn = openLogin)
            }
            composable(Routes.LOGIN) {
                LoginScreen(onLoggedIn = {
                    startup.markOnboardingSeen()
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                })
            }
            composable(Routes.MAP) {
                MapScreen(
                    onOpenPlace = { placeId -> navController.navigate(Routes.placeDetail(placeId)) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenFilters = { navController.navigate(Routes.FILTERS) },
                    onOpenSos = { navController.navigate(Routes.SOS_REQUEST) }
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenPlace = { placeId -> navController.navigate(Routes.placeDetail(placeId)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.FILTERS) {
                FiltersScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(onCategoryChosen = { navController.switchTab(Routes.MAP) })
            }
            composable(
                route = Routes.FAVORITES,
                arguments = listOf(
                    navArgument("tab") {
                        type = NavType.StringType
                        defaultValue = Routes.FAVORITES_TAB_PLACES
                    }
                )
            ) { entry ->
                FavoritesScreen(
                    initialTab = entry.arguments?.getString("tab") ?: Routes.FAVORITES_TAB_PLACES,
                    onBack = { navController.switchTab(Routes.MAP) },
                    onOpenPlace = { placeId -> navController.navigate(Routes.placeDetail(placeId)) }
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenRoutes = { navController.navigate(Routes.favorites(Routes.FAVORITES_TAB_ROUTES)) },
                    onOpenFavorites = { navController.navigate(Routes.favorites(Routes.FAVORITES_TAB_PLACES)) },
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenHelp = { navController.navigate(Routes.SOS_REQUEST) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSignedOut = { goToLogin() }
                )
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ROUTE_PLANNER_PATTERN) { backStackEntry ->
                val placeId = backStackEntry.arguments?.getString("placeId").orEmpty()
                RouteScreen(
                    destinationPlaceId = placeId,
                    onBack = { navController.popBackStack() },
                    onOpenSos = { navController.navigate(Routes.SOS_REQUEST) }
                )
            }
            composable(Routes.SOS_REQUEST) {
                SosRequestScreen(
                    onDone = { navController.popBackStack() },
                    onRequestLogin = { goToLogin() }
                )
            }
            composable(Routes.PLACE_DETAIL_PATTERN) { backStackEntry ->
                val placeId = backStackEntry.arguments?.getString("placeId").orEmpty()
                PlaceDetailScreen(
                    placeId = placeId,
                    onBack = { navController.popBackStack() },
                    onOpenQuickCheck = { navController.navigate(Routes.quickCheck(placeId)) },
                    onOpenPhotoUpload = { navController.navigate(Routes.photoUpload(placeId)) },
                    onOpenRoute = { navController.navigate(Routes.routePlanner(placeId)) },
                    onRequestLogin = { goToLogin() }
                )
            }
            composable(Routes.QUICK_CHECK_PATTERN) { backStackEntry ->
                val placeId = backStackEntry.arguments?.getString("placeId").orEmpty()
                QuickCheckScreen(
                    placeId = placeId,
                    onDone = { navController.popBackStack() },
                    onRequestLogin = { goToLogin() }
                )
            }
            composable(Routes.PHOTO_UPLOAD_PATTERN) { backStackEntry ->
                val placeId = backStackEntry.arguments?.getString("placeId").orEmpty()
                PhotoUploadScreen(
                    placeId = placeId,
                    onDone = { navController.popBackStack() },
                    onRequestLogin = { goToLogin() }
                )
            }
        }
    }
}

/** Перехід між вкладками нижньої навігації зі збереженням стану; коренем стеку є мапа. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.MAP) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
