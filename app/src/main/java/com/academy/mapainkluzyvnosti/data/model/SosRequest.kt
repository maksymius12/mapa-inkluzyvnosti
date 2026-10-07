package com.academy.mapainkluzyvnosti.data.model

data class SosRequest(
    val id: String,
    val userId: String?,
    val lat: Double,
    val lng: Double,
    val problemType: SosProblemType,
    val comment: String?,
    val status: SosStatus,
    val createdAt: String?
)
