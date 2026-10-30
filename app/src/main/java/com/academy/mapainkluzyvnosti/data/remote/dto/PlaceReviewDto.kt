package com.academy.mapainkluzyvnosti.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Рядок place_reviews. author_name, created_at, updated_at ставить сервер (тригер place_reviews_before_write). */
@Serializable
data class PlaceReviewDto(
    val id: String,
    @SerialName("place_id") val placeId: String,
    @SerialName("user_id") val userId: String,
    val rating: Int,
    val comment: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class PlaceReviewInsertDto(
    @SerialName("place_id") val placeId: String,
    @SerialName("user_id") val userId: String,
    val rating: Int,
    val comment: String?
)
