package com.academy.mapainkluzyvnosti.ui.components

import com.academy.mapainkluzyvnosti.data.model.CheckResult
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory

/**
 * Один критерій перевірки. [key] — колонка checks (вхід) або ключ у checks.criteria (зупинка) і у відповіді
 * analyze-place-photo. [hint] — коротко під назвою; [details] — повне формулювання методики (кнопка «i»).
 */
data class CriterionDef(
    val key: String,
    val title: String,
    val hint: String,
    val critical: Boolean,
    val details: String? = null
)

enum class CriteriaKind { ENTRANCE, STOP }

/** Набір критеріїв для категорії: заголовок картки й перелік. */
data class CriteriaSet(val kind: CriteriaKind, val cardTitle: String, val criteria: List<CriterionDef>)

/**
 * 7 критеріїв доступності входу — колонки checks. Критичні (пандус, двері, поріг) визначають «Бар'єр»
 * у ComputeAccessStatus і тригері checks_after_insert_apply.
 */
enum class AccessCriterion(val key: String, val title: String, val hint: String, val critical: Boolean) {
    RAMP("ramp", "Пандус", "Або вхід на рівні тротуару", critical = true),
    DOOR_WIDTH("door_width", "Ширина дверей", "Не менше 0,8 м", critical = true),
    THRESHOLD("threshold", "Поріг", "Не вище 2 см", critical = true),
    ELEVATOR("elevator", "Ліфт або перший поверх", "Без сходів усередині", critical = false),
    TOILET("toilet", "Доступний туалет", "Кабіна з місцем для візка", critical = false),
    TACTILE("tactile", "Тактильні позначки", "Для людей з порушенням зору", critical = false),
    STAFF("staff_assistance", "Допомога персоналу", "Працівники допоможуть зайти", critical = false);

    val def: CriterionDef get() = CriterionDef(key, title, hint, critical)

    fun of(check: CheckResult): Boolean = when (this) {
        RAMP -> check.ramp
        DOOR_WIDTH -> check.doorWidth
        THRESHOLD -> check.threshold
        ELEVATOR -> check.elevator
        TOILET -> check.toilet
        TACTILE -> check.tactile
        STAFF -> check.staffAssistance
    }
}

val EntranceCriteria = CriteriaSet(
    kind = CriteriaKind.ENTRANCE,
    cardTitle = "Оцініть вхід до закладу",
    criteria = AccessCriterion.entries.map { it.def }
)

/**
 * 9 критеріїв зупинки громадського транспорту (методика «Мапи безбар'єрності», ЛУН, критерії 7200880–7200888).
 * Ключі збігаються з public.criteria_definitions (міграція 20261105130000) — сервер рахує статус за ними.
 * Критичні — висота посадкового майданчика й вільний доступ без перешкод.
 */
val StopCriteria = CriteriaSet(
    kind = CriteriaKind.STOP,
    cardTitle = "Оцініть зупинку",
    criteria = listOf(
        CriterionDef("shelter", "Навіс", "Захист від дощу й сонця", critical = false,
            details = "На зупинці облаштовано навіс"),
        CriterionDef("seating", "Місця для сидіння", "Лава або сидіння на зупинці", critical = false,
            details = "Облаштовано місця для сидіння"),
        CriterionDef("pavilion", "Розташування павільйону", "≥ 2 м від дороги, лава ≥ 3 м", critical = false,
            details = "Лави з навісом (павільйони) стоять не ближче 2 м від проїзної частини, довжина лави не менше 3 м"),
        CriterionDef("tactile_strip", "Тактильні смуги", "Біля краю майданчика й місця посадки", critical = false,
            details = "Є попереджувальна тактильна смуга біля краю посадкового майданчика та інформаційна тактильна " +
                "смуга, що вказує місце посадки (передні двері)"),
        CriterionDef("passenger_info", "Інформування пасажирів", "Табло, розклад, схема руху", critical = false,
            details = "Є засоби інформування пасажирів (електронні табло, таблиці з розкладом, схема руху тощо)"),
        CriterionDef("accessible_transport", "Доступний транспорт", "Не рідше 1 рейсу на годину", critical = false,
            details = "Через зупинку рухається доступний для осіб з інвалідністю транспорт, не рідше ніж 1 рейс на годину"),
        CriterionDef("tactile_route", "Тактильний шлях до зупинки", "Від шкіл, лікарень, ЦНАП тощо", critical = false,
            details = "На пішохідній зоні є тактильні елементи доступності від закладів соцінфраструктури (освіта, " +
                "охорона здоров'я, соцзахист, культура, ЦНАП, спорт тощо) до найближчої зупинки"),
        CriterionDef("platform_height", "Висота посадкового майданчика", "20–25 см, урівень із низькою підлогою", critical = true,
            details = "Висота посадкового майданчика 20–25 см від рівня дороги, на рівні з низькою підлогою рухомого " +
                "складу (або не нижче ніж 3 см від рівня підлоги)"),
        CriterionDef("obstacle_free", "Вільний доступ", "Без огорож, стовпчиків, стендів", critical = true,
            details = "Доступ до зупинки вільний від перешкод для пішоходів (споруди, огорожі, колони, опори, стенди, " +
                "стовпчики, антипаркувальні напівсфери тощо)")
    )
)

/** Зупинки мають власні 9 критеріїв; усі інші категорії — 7 критеріїв входу. */
fun criteriaFor(category: PlaceCategory): CriteriaSet =
    if (category == PlaceCategory.TRANSIT) StopCriteria else EntranceCriteria

/** Відповіді за ключами критеріїв входу (для ШІ-аналізу і повторної перевірки). */
fun CheckResult.toAnswers(): Map<String, Boolean> = AccessCriterion.entries.associate { it.key to it.of(this) }

/** Відповіді за ключами -> перевірка входу; null, якщо бракує хоч одного з 7. */
fun entranceCheckFrom(answers: Map<String, Boolean?>, accessibleParking: Boolean): CheckResult? {
    fun v(c: AccessCriterion) = answers[c.key]
    if (AccessCriterion.entries.any { v(it) == null }) return null
    return CheckResult(
        ramp = v(AccessCriterion.RAMP)!!,
        doorWidth = v(AccessCriterion.DOOR_WIDTH)!!,
        threshold = v(AccessCriterion.THRESHOLD)!!,
        elevator = v(AccessCriterion.ELEVATOR)!!,
        toilet = v(AccessCriterion.TOILET)!!,
        tactile = v(AccessCriterion.TACTILE)!!,
        staffAssistance = v(AccessCriterion.STAFF)!!,
        accessibleParking = accessibleParking
    )
}
