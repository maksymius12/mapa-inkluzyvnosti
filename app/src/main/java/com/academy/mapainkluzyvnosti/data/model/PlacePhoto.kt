package com.academy.mapainkluzyvnosti.data.model

data class PlacePhoto(
    val id: String,
    val placeId: String,
    val userId: String,
    val url: String,
    val aiTitle: String?,
    val aiSuggestions: List<String>,
    val aiSuggestedStatus: AccessStatus?,
    val createdAt: String
)
