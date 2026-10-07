package com.academy.mapainkluzyvnosti.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val MAP = "map"
    const val SEARCH = "search"
    const val FILTERS = "filters"
    const val FAVORITES = "favorites"
    const val PROFILE = "profile"
    const val ROUTE_PLANNER_PATTERN = "route_planner/{placeId}"
    fun routePlanner(placeId: String) = "route_planner/$placeId"
    const val SOS_REQUEST = "sos_request"

    const val PLACE_DETAIL_PATTERN = "place_detail/{placeId}"
    fun placeDetail(placeId: String) = "place_detail/$placeId"

    const val QUICK_CHECK_PATTERN = "quick_check/{placeId}"
    fun quickCheck(placeId: String) = "quick_check/$placeId"

    const val PHOTO_UPLOAD_PATTERN = "photo_upload/{placeId}"
    fun photoUpload(placeId: String) = "photo_upload/$placeId"

    /** Топ-рівневі вкладки нижньої навігації. */
    val bottomNavRoutes = setOf(MAP, FAVORITES, PROFILE)
}
