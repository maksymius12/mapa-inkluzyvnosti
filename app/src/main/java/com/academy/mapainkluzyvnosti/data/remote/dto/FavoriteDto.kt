package com.academy.mapainkluzyvnosti.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FavoriteDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("place_id") val placeId: String
)
