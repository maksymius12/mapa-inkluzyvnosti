package com.academy.mapainkluzyvnosti.ui.screens.whatsnew

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

data class WhatsNewItem(val icon: ImageVector, val title: String, val description: String)

/** Нове у версії [versionCode] (= versionCode з app/build.gradle.kts). */
data class WhatsNewRelease(val versionCode: Int, val versionName: String, val items: List<WhatsNewItem>)

/**
 * Екран «Що нового» після оновлення. Для нового релізу — додайте запис у початок списку
 * з тим самим versionCode, що й у збірці. Пунктів — 5–6 найважливіших, мовою користувача.
 */
val whatsNewReleases: List<WhatsNewRelease> = listOf(
    WhatsNewRelease(
        versionCode = 2,
        versionName = "1.1.0",
        items = listOf(
            WhatsNewItem(
                Icons.Filled.Sos,
                "SOS одразу",
                "Запит допомоги надсилається миттєво, а деталі можна додати після."
            ),
            WhatsNewItem(
                Icons.Filled.MyLocation,
                "Надійніша геолокація",
                "Працює, навіть коли GPS глушать під час тривоги. Місце можна вказати вручну."
            ),
            WhatsNewItem(
                Icons.Filled.Schedule,
                "Графік роботи",
                "На картці закладу видно, відчинено зараз чи ні."
            ),
            WhatsNewItem(
                Icons.Filled.Newspaper,
                "Новини",
                "Нова вкладка з оголошеннями. Фільтр категорій тепер на мапі."
            ),
            WhatsNewItem(
                Icons.Filled.Map,
                "Зрозуміліші мітки на мапі",
                "Іконка показує категорію, а значок у куті — доступність."
            ),
            WhatsNewItem(
                Icons.Filled.Star,
                "Відгуки та рейтинги",
                "Оцінюйте заклади й читайте відгуки інших, як у Google Maps."
            )
        )
    )
)
