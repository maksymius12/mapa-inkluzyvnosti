package com.academy.mapainkluzyvnosti.data.model

/** Тип доступності для колірної легенди мапи. Порядок = пріоритет, коли у закладу їх кілька. */
enum class AccessibilityType { RAMP, TOILET, PARKING, ELEVATOR, STAFF_HELP }

/** Які типи доступності підтверджені для закладу (за останньою перевіркою та атрибутом паркування). */
val Place.accessibilityTypes: List<AccessibilityType>
    get() = buildList {
        if (checks?.ramp == true) add(AccessibilityType.RAMP)
        if (checks?.toilet == true) add(AccessibilityType.TOILET)
        if (hasAccessibleParking) add(AccessibilityType.PARKING)
        if (checks?.elevator == true) add(AccessibilityType.ELEVATOR)
        if (checks?.staffAssistance == true) add(AccessibilityType.STAFF_HELP)
    }

/** Тип, яким заклад позначається на мапі з урахуванням активного фільтра; null — немає підтверджених типів. */
fun Place.markerType(activeTypes: Set<AccessibilityType>): AccessibilityType? =
    accessibilityTypes.firstOrNull { it in activeTypes }
