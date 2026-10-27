package com.academy.mapainkluzyvnosti.data.model

/** Заявка мешканця на нову локацію — очікує розгляду волонтером, учнем чи адміном. */
data class PlaceRequest(
    val id: String,
    val requesterId: String,
    val name: String,
    val address: String,
    val category: PlaceCategory,
    val note: String?,
    val status: PlaceRequestStatus,
    val rejectReason: String?,
    val createdAt: String
)
