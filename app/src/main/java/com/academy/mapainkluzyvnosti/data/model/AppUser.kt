package com.academy.mapainkluzyvnosti.data.model

data class AppUser(
    val id: String,
    val name: String,
    val purposeRole: UserPurposeRole,
    val ageGroup: UserAgeGroup,
    val points: Int = 0,
    val checksCount: Int = 0
)
