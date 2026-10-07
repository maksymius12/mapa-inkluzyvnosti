package com.academy.mapainkluzyvnosti.data.remote.dto

import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    val name: String,
    @SerialName("purpose_role") val purposeRole: UserPurposeRole,
    @SerialName("age_group") val ageGroup: UserAgeGroup,
    val points: Int = 0,
    @SerialName("checks_count") val checksCount: Int = 0
)
