package com.academy.mapainkluzyvnosti.data.model

data class AppUser(
    val id: String,
    val name: String,
    val purposeRole: UserPurposeRole,
    val ageGroup: UserAgeGroup,
    /** Системна роль (адмін має повний доступ до програми, окрім звичайних прав purposeRole). */
    val role: UserRole = UserRole.USER,
    val points: Int = 0,
    val checksCount: Int = 0,
    val email: String? = null,
    /**
     * Мету й вікову категорію підставлено (рядка в profiles немає або його створив backfill) —
     * застосунок один раз запропонує перевірити їх у Налаштуваннях.
     */
    val rolesNeedReview: Boolean = false,
    /** Інструктаж уже переглянуто (прапорець із profiles, переживає перевстановлення). */
    val onboardingDone: Boolean = false
) {
    val isAdmin: Boolean get() = role == UserRole.ADMIN

    /** Може розглядати заявки мешканців: волонтер, учень (мета користування) або адмін. */
    val isReviewer: Boolean get() = isAdmin || purposeRole == UserPurposeRole.VOLUNTEER || purposeRole == UserPurposeRole.STUDENT
}
