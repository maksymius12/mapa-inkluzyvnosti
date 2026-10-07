package com.academy.mapainkluzyvnosti.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Review(
    val id: String,
    @SerialName("place_id") val placeId: String,
    @SerialName("user_id") val userId: String,
    val rating: Int,
    val text: String,
    @SerialName("created_at") val createdAt: String
)
