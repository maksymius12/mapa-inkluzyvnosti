package com.academy.mapainkluzyvnosti.data.remote.dto

import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Рядок profiles (лише читання: створює його серверний тригер handle_new_user_profile або backfill).
 * Поля, яких може не бути в старих рядках чи до відповідних міграцій, — nullable або з типовими значеннями,
 * щоб профіль не «зникав» через помилку розбору.
 */
@Serializable
data class ProfileDto(
    val id: String,
    val name: String? = null,
    @SerialName("purpose_role") val purposeRole: UserPurposeRole = UserPurposeRole.RESIDENT,
    @SerialName("age_group") val ageGroup: UserAgeGroup = UserAgeGroup.ADULT,
    val points: Int? = null,
    @SerialName("checks_count") val checksCount: Int? = null,
    /** false — мету й вікову категорію ще не обирали (перший вхід через Google). */
    @SerialName("roles_confirmed") val rolesConfirmed: Boolean? = null,
    /** true — мету й вік підставлено за замовчуванням (backfill), варто перевірити в Налаштуваннях. */
    @SerialName("roles_defaulted") val rolesDefaulted: Boolean? = null,
    /** Користувач уже бачив інструктаж «Як користуватися». */
    @SerialName("onboarding_done") val onboardingDone: Boolean? = null
)
