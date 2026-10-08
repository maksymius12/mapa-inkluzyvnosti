package com.academy.mapainkluzyvnosti.ui.navigation

object Routes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val MAP = "map"
    const val SEARCH = "search"
    const val FILTERS = "filters"
    const val CATEGORIES = "categories"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"

    const val FAVORITES_TAB_PLACES = "places"
    const val FAVORITES_TAB_ROUTES = "routes"
    const val FAVORITES = "favorites?tab={tab}"
    fun favorites(tab: String = FAVORITES_TAB_PLACES) = "favorites?tab=$tab"

    const val ROUTE_PLANNER_PATTERN = "route_planner/{placeId}"
    fun routePlanner(placeId: String) = "route_planner/$placeId"
    const val SOS_REQUEST = "sos_request"

    const val PLACE_DETAIL_PATTERN = "place_detail/{placeId}"
    fun placeDetail(placeId: String) = "place_detail/$placeId"

    const val QUICK_CHECK_PATTERN = "quick_check/{placeId}"
    fun quickCheck(placeId: String) = "quick_check/$placeId"

    const val PHOTO_UPLOAD_PATTERN = "photo_upload/{placeId}"
    fun photoUpload(placeId: String) = "photo_upload/$placeId"

    /** Топ-рівневі вкладки нижньої навігації (маршрути-шаблони, як їх віддає NavDestination). */
    val bottomNavRoutes = setOf(MAP, CATEGORIES, FAVORITES, PROFILE)
}
