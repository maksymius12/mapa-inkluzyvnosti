package com.academy.mapainkluzyvnosti.ui.screens.settings

import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole

fun purposeRoleLabel(role: UserPurposeRole): String = when (role) {
    UserPurposeRole.RESIDENT -> "Мешканець"
    UserPurposeRole.VOLUNTEER -> "Волонтер"
    UserPurposeRole.STUDENT -> "Учень"
}

fun ageGroupLabel(group: UserAgeGroup): String = when (group) {
    UserAgeGroup.STUDENT -> "Підліток"
    UserAgeGroup.ADULT -> "Дорослий"
}
