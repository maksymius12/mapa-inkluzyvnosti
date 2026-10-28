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
import com.academy.mapainkluzyvnosti.ui.screens.login.OtpPurpose
import com.academy.mapainkluzyvnosti.ui.screens.login.VerifyCodeScreen
import com.academy.mapainkluzyvnosti.ui.screens.login.RoleSetupScreen
import com.academy.mapainkluzyvnosti.ui.screens.login.SignUpScreen
import com.academy.mapainkluzyvnosti.ui.screens.map.MapScreen
import com.academy.mapainkluzyvnosti.ui.screens.notifications.NotificationsScreen
import com.academy.mapainkluzyvnosti.ui.screens.photoupload.PhotoUploadScreen
import com.academy.mapainkluzyvnosti.ui.screens.placedetail.PlaceDetailScreen
import com.academy.mapainkluzyvnosti.ui.screens.profile.ProfileScreen
import com.academy.mapainkluzyvnosti.ui.screens.admin.AdminUsersScreen
import com.academy.mapainkluzyvnosti.ui.screens.requests.PlaceRequestsScreen
import com.academy.mapainkluzyvnosti.ui.screens.quickcheck.QuickCheckScreen
import com.academy.mapainkluzyvnosti.ui.screens.route.RouteScreen
import com.academy.mapainkluzyvnosti.ui.screens.search.SearchScreen
import com.academy.mapainkluzyvnosti.ui.screens.settings.SettingsScreen
import com.academy.mapainkluzyvnosti.ui.screens.sos.SosRequestScreen
import com.academy.mapainkluzyvnosti.ui.screens.welcome.WelcomeScreen
import com.academy.mapainkluzyvnosti.ui.screens.tutorial.TutorialScreen
import com.academy.mapainkluzyvnosti.ui.state.AppStartup
import com.academy.mapainkluzyvnosti.ui.state.TutorialCoordinator
import com.academy.mapainkluzyvnosti.ui.state.TutorialMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun MapaNavHost(startRoute: String) {
    val navController = rememberNavController()
    val startup = koinInject<AppStartup>()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.bottomNavRoutes

    fun goToLogin() = navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }

    val koinTutorial = koinInject<TutorialCoordinator>()

    /** Після входу/реєстрації: мапа стає коренем стеку, екрани входу прибираються. */
    fun goToMapAfterAuth() {
        startup.markOnboardingSeen()
        navController.navigate(Routes.MAP) { popUpTo(navController.graph.id) { inclusive = true } }
    }

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
                    ),
                    onSos = { navController.navigate(Routes.SOS_REQUEST) }
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
                LoginScreen(
                    onLoggedIn = { goToMapAfterAuth() },
                    onNeedsRoleSetup = {
                        startup.markOnboardingSeen()
                        navController.navigate(Routes.ROLE_SETUP) { popUpTo(navController.graph.id) { inclusive = true } }
                    },
                    onOpenSignUp = { navController.navigate(Routes.SIGN_UP) },
                    onOpenVerifyCode = { request ->
                        navController.navigate(Routes.verifyCode(request.purpose, request.email, request.sendCode))
                    }
                )
            }
            composable(Routes.SIGN_UP) {
                SignUpScreen(
                    onSignedIn = { goToMapAfterAuth() },
                    onCodeSent = { email -> navController.navigate(Routes.verifyCode(OtpPurpose.SIGN_UP, email, sendCode = false)) },
                    onOpenLogin = {
                        // Зазвичай реєстрацію відкривають з екрана входу — просто повертаємось до нього.
                        if (!navController.popBackStack(Routes.LOGIN, inclusive = false)) goToLogin()
                    }
                )
            }
            composable(
                route = Routes.VERIFY_CODE_PATTERN,
                arguments = listOf(
                    navArgument("purpose") { type = NavType.StringType },
                    navArgument("email") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("send") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { entry ->
                val purpose = entry.arguments?.getString("purpose")
                    ?.let { name -> OtpPurpose.entries.firstOrNull { it.name == name } }
                    ?: OtpPurpose.SIGN_UP
                VerifyCodeScreen(
                    purpose = purpose,
                    email = entry.arguments?.getString("email").orEmpty(),
                    sendCodeOnStart = entry.arguments?.getBoolean("send") ?: false,
                    onSignedIn = { goToMapAfterAuth() },
                    onNeedsRoleSetup = {
                        startup.markOnboardingSeen()
                        navController.navigate(Routes.ROLE_SETUP) { popUpTo(navController.graph.id) { inclusive = true } }
                    },
                    onChangeEmail = {
                        // Після реєстрації — назад на крок 1 з уже заповненими полями; після входу — нова реєстрація.
                        if (!navController.popBackStack(Routes.SIGN_UP, inclusive = false)) {
                            navController.navigate(Routes.SIGN_UP) {
                                popUpTo(Routes.VERIFY_CODE_PATTERN) { inclusive = true }
                            }
                        }
                    },
                    onBackToLogin = {
                        if (!navController.popBackStack(Routes.LOGIN, inclusive = false)) goToLogin()
                    }
                )
            }
            composable(
                route = Routes.TUTORIAL_PATTERN,
                arguments = listOf(
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = TutorialMode.FULL.name
                    }
                )
            ) { entry ->
                val mode = entry.arguments?.getString("mode")
                    ?.let { name -> TutorialMode.entries.firstOrNull { it.name == name } }
                    ?: TutorialMode.FULL
                TutorialScreen(mode = mode, onFinished = { navController.popBackStack() })
            }
            composable(Routes.ROLE_SETUP) {
                RoleSetupScreen(
                    onDone = { goToMapAfterAuth() },
                    onNeedsSignIn = { goToLogin() }
                )
            }
            composable(Routes.MAP) {
                val tutorial = koinInject<TutorialCoordinator>()
                val scope = rememberCoroutineScope()
                var showRolesPrompt by remember { mutableStateOf(false) }
                // Інструктаж — один раз після першої реєстрації/входу (чи гостьового входу);
                // інакше — одноразова підказка перевірити підставлені мету й вік.
                LaunchedEffect(Unit) {
                    val pending = tutorial.pendingTutorial()
                    if (pending != null) {
                        navController.navigate(Routes.tutorial(pending)) { launchSingleTop = true }
                    } else if (tutorial.shouldPromptRolesReview()) {
                        showRolesPrompt = true
                    }
                }
                if (showRolesPrompt) {
                    val close = {
                        showRolesPrompt = false
                        scope.launch { tutorial.markRolesPromptShown() }
                    }
                    AlertDialog(
                        onDismissRequest = { close() },
                        title = { Text("Перевірте профіль") },
                        text = {
                            Text(
                                "Мету користування й вікову категорію підставлено за замовчуванням. " +
                                    "Від них залежать можливості — наприклад, фото місць додають лише дорослі. " +
                                    "Перевірте їх у Налаштуваннях."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                close()
                                navController.navigate(Routes.SETTINGS)
                            }) { Text("Відкрити Налаштування") }
                        },
                        dismissButton = { TextButton(onClick = { close() }) { Text("Пізніше") } }
                    )
                }
                MapScreen(
                    onOpenPlace = { placeId -> navController.navigate(Routes.placeDetail(placeId)) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenFilters = { navController.navigate(Routes.FILTERS) },
                    onOpenQuickCheck = { placeId -> navController.navigate(Routes.quickCheck(placeId)) },
                    onRequestLogin = { goToLogin() }
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
                    onOpenHelp = { navController.navigate(Routes.SOS_REQUEST) },
                    onOpenTutorial = { navController.navigate(Routes.tutorial(tutorialMode(koinTutorial))) },
                    onOpenPlaceRequests = { navController.navigate(Routes.PLACE_REQUESTS) },
                    onOpenAdmin = { navController.navigate(Routes.ADMIN_USERS) },
                    onSignedOut = { goToLogin() }
                )
            }
            composable(Routes.PLACE_REQUESTS) {
                PlaceRequestsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ADMIN_USERS) {
                AdminUsersScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSignedOut = { goToLogin() },
                    onOpenTutorial = { navController.navigate(Routes.tutorial(tutorialMode(koinTutorial))) }
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

/** Повторний перегляд: гість бачить скорочену версію, акаунт — повну. */
private fun tutorialMode(coordinator: TutorialCoordinator): TutorialMode = coordinator.currentMode
