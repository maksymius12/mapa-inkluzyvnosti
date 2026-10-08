package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.DirectionsTransit
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory

data class CategoryVisual(val color: Color, val icon: ImageVector, val label: String)

val PlaceCategory.visual: CategoryVisual
    get() = when (this) {
        PlaceCategory.EDUCATION -> CategoryVisual(Color(0xFF3E7BFA), Icons.Outlined.School, "Освіта")
        PlaceCategory.HEALTH -> CategoryVisual(Color(0xFFE8547A), Icons.Outlined.LocalHospital, "Медицина")
        PlaceCategory.CULTURE -> CategoryVisual(Color(0xFF9B5DE5), Icons.Outlined.Museum, "Культура")
        PlaceCategory.SPORT -> CategoryVisual(Color(0xFF2FA37A), Icons.Outlined.SportsSoccer, "Спорт")
        PlaceCategory.CAFE -> CategoryVisual(Color(0xFFE08A3A), Icons.Outlined.LocalCafe, "Кафе та ресторани")
        PlaceCategory.SERVICES -> CategoryVisual(Color(0xFF4AA3C4), Icons.Outlined.Handyman, "Сервіси")
        PlaceCategory.ADMIN -> CategoryVisual(Color(0xFF6B7280), Icons.Outlined.AccountBalance, "Адміністрація")
        PlaceCategory.SHOP -> CategoryVisual(Color(0xFFD6558C), Icons.Outlined.Storefront, "Магазин")
        PlaceCategory.TRANSIT -> CategoryVisual(Color(0xFFC9A227), Icons.Outlined.DirectionsTransit, "Транспорт")
    }

/**
 * Категорії у фільтрах — шість груп, як на макеті. Адміністрація, магазини й транспорт
 * об'єднані з «Сервісами», щоб кожне місце належало до якоїсь групи фільтра.
 */
enum class CategoryGroup(val label: String, val categories: Set<PlaceCategory>) {
    EDUCATION("Освіта", setOf(PlaceCategory.EDUCATION)),
    HEALTH("Медицина", setOf(PlaceCategory.HEALTH)),
    CULTURE("Культура", setOf(PlaceCategory.CULTURE)),
    SPORT("Спорт", setOf(PlaceCategory.SPORT)),
    FOOD("Кафе та ресторани", setOf(PlaceCategory.CAFE)),
    SERVICES("Сервіси", setOf(PlaceCategory.SERVICES, PlaceCategory.ADMIN, PlaceCategory.SHOP, PlaceCategory.TRANSIT));

    val visual: CategoryVisual get() = categories.first().visual
}

fun AccessStatus.color(): Color = when (this) {
    AccessStatus.ACCESSIBLE -> com.academy.mapainkluzyvnosti.ui.theme.StatusAccessible
    AccessStatus.PARTIAL -> com.academy.mapainkluzyvnosti.ui.theme.StatusPartial
    AccessStatus.BARRIER -> com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier
    AccessStatus.UNVERIFIED -> com.academy.mapainkluzyvnosti.ui.theme.StatusUnverified
}

fun AccessStatus.label(): String = when (this) {
    AccessStatus.ACCESSIBLE -> "Доступно"
    AccessStatus.PARTIAL -> "Частково доступно"
    AccessStatus.BARRIER -> "Бар'єр"
    AccessStatus.UNVERIFIED -> "Не перевірено"
}
