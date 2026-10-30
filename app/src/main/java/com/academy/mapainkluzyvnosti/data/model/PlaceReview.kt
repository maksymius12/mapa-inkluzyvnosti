package com.academy.mapainkluzyvnosti.data.model

/** Відгук до місця: оцінка 1–5 зірок і необов'язковий коментар. Один на місце від користувача. */
data class PlaceReview(
    val id: String,
    val userId: String,
    val authorName: String,
    val rating: Int,
    val comment: String?,
    val createdAt: String,
    val updatedAt: String
)
