package com.academy.mapainkluzyvnosti.ui.screens.tutorial

import com.academy.mapainkluzyvnosti.ui.state.TutorialMode

enum class TutorialIllustration { MAP_MARKERS, FIND, CHECK_CARD, ROLES, SIGN_IN }

/** Слайд інструктажу. [illustrationDescription] читає TalkBack замість малюнка. */
data class TutorialSlide(
    val title: String,
    val text: String,
    val illustration: TutorialIllustration,
    val illustrationDescription: String
)

// Як у PlaceMarker: іконка — категорія закладу, значок у куті — статус доступності.
private val mapSlide = TutorialSlide(
    title = "Мапа й позначки",
    text = "Іконка позначки — категорія закладу: освіта, медицина, кафе, магазин тощо. Маленький значок у куті — " +
        "доступність: зелений — виконано всі 7 критеріїв, жовтий — частково доступно, червоний — бракує " +
        "критичного критерію, пунктир — ще не перевірено.",
    illustration = TutorialIllustration.MAP_MARKERS,
    illustrationDescription = "Ілюстрація: позначки з іконками категорій — школа, лікарня, кафе, магазин — " +
        "і значками доступності в куті: зеленим, жовтим, червоним і пунктирним"
)

private val findSlide = TutorialSlide(
    title = "Знайди й дійди",
    text = "Шукай за назвою чи адресою, а категорію вибирай фільтром просто на мапі. На картці місця — статус " +
        "доступності, графік роботи й позначка «Відчинено зараз» або «Зачинено». «Будувати маршрут» попередить " +
        "про бар'єри на шляху. Оголошення — на вкладці «Новини».",
    illustration = TutorialIllustration.FIND,
    illustrationDescription = "Ілюстрація: рядок пошуку, маршрут до місця й позначка «Відчинено зараз»"
)

private val checkSlide = TutorialSlide(
    title = "Оцініть вхід до закладу",
    text = "«Пройти перевірку» на картці місця відкриває 7 критеріїв із перемикачами «Так | Ні» — спершу нічого " +
        "не вибрано. Критичні: пандус, ширина дверей, поріг. Додаткові: ліфт або перший поверх, туалет, " +
        "тактильні позначки, допомога персоналу. Зберегти можна, коли оцінено всі. Зупинки транспорту мають " +
        "окремі 9 критеріїв («Оцініть зупинку»): критичні — висота посадкового майданчика й вільний доступ.",
    illustration = TutorialIllustration.CHECK_CARD,
    illustrationDescription = "Ілюстрація: картка перевірки з групами «Критичні» й «Додаткові» та перемикачами «Так» і «Ні»"
)

private val rolesSlide = TutorialSlide(
    title = "Хто що може",
    text = "Мешканець пропонує зміни: нове місце — заявкою, нову назву — кнопкою «Запропонувати зміну назви»; " +
        "статус видно в Профіль → «Мої заявки». Волонтери, учні й адмін розглядають заявки, додають місця одразу " +
        "й редагують графік роботи. Відгук і оцінку може залишити кожен, хто увійшов.",
    illustration = TutorialIllustration.ROLES,
    illustrationDescription = "Ілюстрація: мешканець надсилає пропозицію, волонтер її схвалює"
)

private val guestContributeSlide = TutorialSlide(
    title = "Допоможи іншим",
    text = "Увійдіть, щоб перевіряти місця, залишати відгуки й пропонувати нові місця.",
    illustration = TutorialIllustration.SIGN_IN,
    illustrationDescription = "Ілюстрація: вхід в акаунт"
)

/** Повний інструктаж — 4 слайди. Гість — 2: що на мапі й як отримати решту можливостей. */
fun tutorialSlides(mode: TutorialMode): List<TutorialSlide> = when (mode) {
    TutorialMode.FULL -> listOf(mapSlide, findSlide, checkSlide, rolesSlide)
    TutorialMode.GUEST -> listOf(mapSlide, guestContributeSlide)
}
