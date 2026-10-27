package com.academy.mapainkluzyvnosti.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PlaceCategory {
    @SerialName("education") EDUCATION,
    @SerialName("health") HEALTH,
    @SerialName("culture") CULTURE,
    @SerialName("sport") SPORT,
    @SerialName("cafe") CAFE,
    @SerialName("services") SERVICES,
    @SerialName("admin") ADMIN,
    @SerialName("shop") SHOP,
    @SerialName("transit") TRANSIT
}

@Serializable
enum class AccessStatus {
    @SerialName("accessible") ACCESSIBLE,
    @SerialName("partial") PARTIAL,
    @SerialName("barrier") BARRIER,
    @SerialName("unverified") UNVERIFIED
}

@Serializable
enum class UserPurposeRole {
    @SerialName("resident") RESIDENT,
    @SerialName("volunteer") VOLUNTEER,
    /** Учень, який долучається як дослідник/рецензент (на відміну від вікової категорії). */
    @SerialName("student") STUDENT
}

@Serializable
enum class UserAgeGroup {
    @SerialName("student") STUDENT,
    @SerialName("adult") ADULT
}

/** Системна роль акаунта в застосунку: звичайний користувач чи адміністратор із повним доступом. */
@Serializable
enum class UserRole {
    @SerialName("user") USER,
    @SerialName("admin") ADMIN
}

/** Звідки користувач дізнався про застосунок — питається один раз при реєстрації. */
@Serializable
enum class ReferralSource {
    @SerialName("school") SCHOOL,
    @SerialName("friends") FRIENDS,
    @SerialName("social_media") SOCIAL_MEDIA,
    @SerialName("search") SEARCH,
    @SerialName("other") OTHER
}

/** Статус заявки мешканця на нову локацію. */
@Serializable
enum class PlaceRequestStatus {
    @SerialName("pending") PENDING,
    @SerialName("approved") APPROVED,
    @SerialName("rejected") REJECTED
}

@Serializable
enum class SosProblemType {
    @SerialName("obstacle") OBSTACLE,
    @SerialName("physical_help") PHYSICAL_HELP,
    @SerialName("other") OTHER
}

@Serializable
enum class SosStatus {
    @SerialName("open") OPEN,
    @SerialName("resolved") RESOLVED
}
