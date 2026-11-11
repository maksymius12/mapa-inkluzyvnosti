package com.academy.mapainkluzyvnosti.ui.screens.whatsnew

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Schedule
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
        versionCode = 5,
        versionName = "1.2.0",
        items = listOf(
            WhatsNewItem(
                Icons.AutoMirrored.Filled.Chat,
                "Спільний чат",
                "Профіль → «Чат»: одна розмова для всіх, хто увійшов. Лише текст, до 500 символів."
            ),
            WhatsNewItem(
                Icons.Filled.Sos,
                "SOS повернувся",
                "Червона кнопка по центру нижньої панелі: утримайте її, і запит допомоги одразу піде волонтерам."
            ),
            WhatsNewItem(
                Icons.Filled.Favorite,
                "Обране й маршрути",
                "Тепер лише у вкладці «Обране» внизу екрана. З профілю ці пункти прибрано."
            )
        )
    ),
    WhatsNewRelease(
        versionCode = 2,
        versionName = "1.1.0",
        items = listOf(
            WhatsNewItem(
                Icons.AutoMirrored.Filled.PlaylistAddCheck,
                "Нова картка перевірки",
                "«Так | Ні» для кожного критерію, спершу нічого не вибрано. Зупинки транспорту — окремі 9 критеріїв."
            ),
            WhatsNewItem(
                Icons.Filled.MyLocation,
                "Надійніша геолокація",
                "Мапа не стрибає за хибною позицією, коли GPS глушать під час тривоги."
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
