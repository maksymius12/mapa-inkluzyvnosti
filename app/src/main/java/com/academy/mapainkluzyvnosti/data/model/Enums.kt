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
    @SerialName("volunteer") VOLUNTEER
}

@Serializable
enum class UserAgeGroup {
    @SerialName("student") STUDENT,
    @SerialName("adult") ADULT
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
