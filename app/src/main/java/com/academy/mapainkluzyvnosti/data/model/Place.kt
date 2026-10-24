package com.academy.mapainkluzyvnosti.data.model

data class Place(
    val id: String,
    val name: String,
    val address: String,
    val category: PlaceCategory,
    val lat: Double,
    val lng: Double,
    val status: AccessStatus,
    val checks: CheckResult?,
    val source: String,
    val verifiedNote: String,
    val rating: Double? = null,
    val reviewCount: Int = 0,
    /** Паркування для МГН — додатковий атрибут, у розрахунок статусу не входить. */
    val hasAccessibleParking: Boolean = false,
    /** Автор місця, доданого користувачем; null — OSM або старі записи. */
    val createdBy: String? = null,
    /** Підпис джерела для місць із відкритих даних (вимога ліцензії CC BY). */
    val attribution: String? = null
) {
    val isUserAdded: Boolean get() = source == PLACE_SOURCE_USER

    /** Імпортовано з відкритих даних «Мапи безбар'єрності» (data.gov.ua). */
    val isOpenData: Boolean get() = source == PLACE_SOURCE_OPEN_DATA

    /** Додане користувачем місце, яке ще ніхто не перевірив, — на мапі пунктирна мітка категорії. */
    val awaitsFirstCheck: Boolean get() = isUserAdded && checks == null && status == AccessStatus.UNVERIFIED
}

const val PLACE_SOURCE_USER = "user"
const val PLACE_SOURCE_OPEN_DATA = "open_data"

/** Підпис на картці місця з відкритих даних, якщо в записі немає власного. */
const val OPEN_DATA_ATTRIBUTION = "Дані: Мапа безбар'єрності (Мінрозвитку, ЛУН Місто), відкриті дані"
