package com.academy.mapainkluzyvnosti.ui.navigation

import android.net.Uri
import com.academy.mapainkluzyvnosti.ui.screens.login.OtpPurpose
import com.academy.mapainkluzyvnosti.ui.state.TutorialMode

object Routes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val SIGN_UP = "sign_up"
    /** Один раз після першого входу через Google: мета користування й вікова категорія. */
    const val ROLE_SETUP = "role_setup"

    /** Інструктаж «Як користуватися»: FULL — для акаунта, GUEST — скорочений для гостя. */
    const val TUTORIAL_PATTERN = "tutorial?mode={mode}"
    fun tutorial(mode: TutorialMode) = "tutorial?mode=${mode.name}"

    /** «Введіть код» з листа: підтвердження реєстрації або скидання пароля. */
    const val VERIFY_CODE_PATTERN = "verify_code/{purpose}?email={email}&send={send}"
    fun verifyCode(purpose: OtpPurpose, email: String, sendCode: Boolean) =
        "verify_code/${purpose.name}?email=${Uri.encode(email)}&send=$sendCode"
    const val MAP = "map"
    const val SEARCH = "search"
    const val FILTERS = "filters"
    const val CATEGORIES = "categories"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"
    /** Заявки на нові локації: черга на розгляд (рецензент) або власні заявки (мешканець). */
    const val PLACE_REQUESTS = "place_requests"
    const val ADMIN_USERS = "admin_users"

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
