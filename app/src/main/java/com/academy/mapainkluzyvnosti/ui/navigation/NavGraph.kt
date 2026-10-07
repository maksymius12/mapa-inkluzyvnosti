package com.academy.mapainkluzyvnosti.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.academy.mapainkluzyvnosti.ui.components.liquidGlass
import com.academy.mapainkluzyvnosti.ui.screens.favorites.FavoritesScreen
import com.academy.mapainkluzyvnosti.ui.screens.filters.FiltersScreen
import com.academy.mapainkluzyvnosti.ui.screens.login.LoginScreen
import com.academy.mapainkluzyvnosti.ui.screens.map.MapScreen
import com.academy.mapainkluzyvnosti.ui.screens.placedetail.PlaceDetailScreen
import com.academy.mapainkluzyvnosti.ui.screens.profile.ProfileScreen
import com.academy.mapainkluzyvnosti.ui.screens.quickcheck.QuickCheckScreen
import com.academy.mapainkluzyvnosti.ui.screens.route.RouteScreen
import com.academy.mapainkluzyvnosti.ui.screens.search.SearchScreen
import com.academy.mapainkluzyvnosti.ui.screens.sos.SosRequestScreen
import dev.chrisbanes.haze.rememberHazeState

/** Висота плаваючої скляної нижньої навігації + її відступ від краю — щоб контент екранів не ховався під нею. */
val BottomNavInset = 92.dp

private data class BottomNavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.MAP, "Карта", Icons.Filled.Map),
    BottomNavItem(Routes.FAVORITES, "Обране", Icons.Filled.Favorite),
    BottomNavItem(Routes.PROFILE, "Профіль", Icons.Filled.Person)
)

@Composable
fun MapaNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.bottomNavRoutes
    val hazeState = rememberHazeState()
    val bottomInset = if (showBottomBar) BottomNavInset else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(onLoggedIn = {
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                })
            }
            composable(Routes.MAP) {
                // Карта — єдиний екран, що навмисно тягнеться під плаваючу скляну навігацію
                // (це і є контент, який крізь неї просвічує розмитим).
                MapScreen(
                    hazeState = hazeState,
                    bottomInset = bottomInset,
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
            composable(Routes.FAVORITES) {
                Box(Modifier.padding(bottom = bottomInset)) {
                    FavoritesScreen(onOpenPlace = { placeId -> navController.navigate(Routes.placeDetail(placeId)) })
                }
            }
            composable(Routes.PROFILE) {
                Box(Modifier.padding(bottom = bottomInset)) {
                    ProfileScreen(
                        onSignedOut = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
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
                    onRequestLogin = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
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
                    onRequestLogin = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
                )
            }
            composable(Routes.QUICK_CHECK_PATTERN) { backStackEntry ->
                val placeId = backStackEntry.arguments?.getString("placeId").orEmpty()
                QuickCheckScreen(
                    placeId = placeId,
                    onDone = { navController.popBackStack() },
                    onRequestLogin = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
                )
            }
            composable(Routes.PHOTO_UPLOAD_PATTERN) { backStackEntry ->
                val placeId = backStackEntry.arguments?.getString("placeId").orEmpty()
                com.academy.mapainkluzyvnosti.ui.screens.photoupload.PhotoUploadScreen(
                    placeId = placeId,
                    onDone = { navController.popBackStack() },
                    onRequestLogin = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
                )
            }
        }

        if (showBottomBar) {
            val barShape = RoundedCornerShape(28.dp)
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .liquidGlass(hazeState, shape = barShape)
            ) {
                bottomNavItems.forEach { item ->
                    val selected = backStackEntry?.destination?.hierarchy
                        ?.any { it.route == item.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.White.copy(alpha = 0.16f))
                    )
                }
            }
        }
    }
}
