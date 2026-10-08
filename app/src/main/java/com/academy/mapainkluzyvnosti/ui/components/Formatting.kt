package com.academy.mapainkluzyvnosti.ui.components

import java.util.Locale

/** «4.8» — крапка як на макеті, незалежно від локалі пристрою. */
fun formatRating(rating: Double): String = String.format(Locale.US, "%.1f", rating)

/** «1 відгук», «3 відгуки», «32 відгуки», «5 відгуків». */
fun reviewsLabel(count: Int): String {
    val lastTwo = count % 100
    val last = count % 10
    val word = when {
        lastTwo in 11..14 -> "відгуків"
        last == 1 -> "відгук"
        last in 2..4 -> "відгуки"
        else -> "відгуків"
    }
    return "$count $word"
}

fun formatDistanceKm(meters: Double): String = String.format(Locale.US, "%.1f км", meters / 1000)

fun formatDurationMinutes(seconds: Double): String = "${maxOf(1, (seconds / 60).toInt())} хв"
