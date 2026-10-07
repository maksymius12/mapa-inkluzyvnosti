package com.academy.mapainkluzyvnosti.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CheckResult(
    val ramp: Boolean,
    @SerialName("door_width") val doorWidth: Boolean,
    val threshold: Boolean,
    val elevator: Boolean,
    val toilet: Boolean,
    val tactile: Boolean,
    @SerialName("staff_assistance") val staffAssistance: Boolean
)
