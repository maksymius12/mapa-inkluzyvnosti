package com.academy.mapainkluzyvnosti.ui.screens.tutorial

import com.academy.mapainkluzyvnosti.ui.state.TutorialMode

enum class TutorialIllustration { MAP_MARKERS, SEARCH, ROUTE, CONTRIBUTE, SIGN_IN, HELP_REQUEST }

/** Слайд інструктажу. [illustrationDescription] читає TalkBack замість малюнка. */
data class TutorialSlide(
    val title: String,
    val text: String,
    val illustration: TutorialIllustration,
    val illustrationDescription: String
)

private val mapSlide = TutorialSlide(
    title = "Мапа й позначки",
    text = "Кожна позначка — це місце. Колір показує, що там є: синій — пандус, зелений — туалет, " +
        "жовтий — паркування, фіолетовий — ліфт, рожевий — допомога персоналу. Сіра — даних ще немає, " +
        "пунктирна — нове місце, яке ще не перевірили. Статус доступності видно на картці місця.",
    illustration = TutorialIllustration.MAP_MARKERS,
    illustrationDescription = "Ілюстрація: фрагмент мапи з кольоровими позначками місць і одною пунктирною позначкою"
)

private val searchSlide = TutorialSlide(
    title = "Знайди потрібне",
    text = "Пошук і фільтри за типом доступності та категорією допоможуть швидко знайти заклад.",
    illustration = TutorialIllustration.SEARCH,
    illustrationDescription = "Ілюстрація: рядок пошуку й кнопки фільтрів"
)

private val routeSlide = TutorialSlide(
    title = "Дізнайся деталі й дійди",
    text = "Натисни на місце, щоб побачити інформацію про доступність. Кнопка «Будувати маршрут» " +
        "прокладе шлях і попередить про бар'єри на ньому.",
    illustration = TutorialIllustration.ROUTE,
    illustrationDescription = "Ілюстрація: маршрут від точки до місця з позначкою бар'єра на шляху"
)

private val contributeSlide = TutorialSlide(
    title = "Допоможи іншим",
    text = "Додай нове місце кнопкою «+ Додати місце» й пройди перевірку за 7 критеріями — це до 1 хвилини. " +
        "Дорослі користувачі можуть додати фото входу.",
    illustration = TutorialIllustration.CONTRIBUTE,
    illustrationDescription = "Ілюстрація: кнопка додавання місця, список перевірки й камера"
)

private val guestContributeSlide = TutorialSlide(
    title = "Допоможи іншим",
    text = "Увійдіть, щоб додавати місця й перевіряти їх.",
    illustration = TutorialIllustration.SIGN_IN,
    illustrationDescription = "Ілюстрація: вхід в акаунт"
)

// Запит допомоги отримують усі волонтери застосунку (без відбору за відстанню), тож без слова «поруч».
private val helpSlide = TutorialSlide(
    title = "Запит допомоги",
    text = "Кнопка «Потрібна допомога» на мапі (або Профіль → Допомога) створює запит: волонтери застосунку " +
        "побачать його на мапі. Це не заміна 112.",
    illustration = TutorialIllustration.HELP_REQUEST,
    illustrationDescription = "Ілюстрація: знак запиту допомоги й волонтери"
)

/**
 * Повний інструктаж — 5 слайдів. Гість — 2: що на мапі й як отримати решту можливостей.
 * Запит допомоги гостю недоступний (демо-режим), тож цього слайда в нього немає.
 */
fun tutorialSlides(mode: TutorialMode): List<TutorialSlide> = when (mode) {
    TutorialMode.FULL -> listOf(mapSlide, searchSlide, routeSlide, contributeSlide, helpSlide)
    TutorialMode.GUEST -> listOf(mapSlide, guestContributeSlide)
}
