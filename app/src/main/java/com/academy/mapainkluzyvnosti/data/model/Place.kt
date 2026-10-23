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
    val createdBy: String? = null
) {
    val isUserAdded: Boolean get() = source == PLACE_SOURCE_USER

    /** Додане користувачем місце, яке ще ніхто не перевірив, — на мапі пунктирна мітка категорії. */
    val awaitsFirstCheck: Boolean get() = isUserAdded && checks == null && status == AccessStatus.UNVERIFIED
}

const val PLACE_SOURCE_USER = "user"
