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
    val hasAccessibleParking: Boolean = false
)
