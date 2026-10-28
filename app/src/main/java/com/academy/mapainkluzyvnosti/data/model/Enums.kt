package com.academy.mapainkluzyvnosti.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PlaceCategory {
    @SerialName("education") EDUCATION,
    @SerialName("health") HEALTH,
    @SerialName("culture") CULTURE,
    /** Бібліотеки — у відкритих даних їх найбільше серед закладів культури, тож окремо. */
    @SerialName("library") LIBRARY,
    @SerialName("sport") SPORT,
    @SerialName("cafe") CAFE,
    @SerialName("services") SERVICES,
    /** ЦНАП — центр надання адміністративних послуг (окремо від інших держустанов). */
    @SerialName("cnap") CNAP,
    /** Органи влади, управління й соцслужби. */
    @SerialName("admin") ADMIN,
    @SerialName("shop") SHOP,
    /** Зупинки громадського транспорту: автобус/тролейбус/трамвай, станції метро й залізниці. */
    @SerialName("transit") TRANSIT
}

/**
 * Категорії, що існували до появи [PlaceCategory.LIBRARY] і [PlaceCategory.CNAP]. Збережені набори
 * фільтрів без позначки «відомих» категорій — старі, тож нові категорії в них додаються увімкненими.
 */
val LEGACY_PLACE_CATEGORY_NAMES: Set<String> =
    setOf("EDUCATION", "HEALTH", "CULTURE", "SPORT", "CAFE", "SERVICES", "ADMIN", "SHOP", "TRANSIT")

/**
 * Відновлює збережений набір категорій: невідомі назви відкидає, а категорії, яких ще не було на момент
 * збереження ([knownNames]), додає — нова категорія не має зникати з мапи лише через старий фільтр.
 */
fun decodeStoredCategories(names: Set<String>, knownNames: Set<String>?): Set<PlaceCategory> {
    val known = knownNames ?: LEGACY_PLACE_CATEGORY_NAMES
    val saved = names.mapNotNull { name -> PlaceCategory.entries.firstOrNull { it.name == name } }
    return (saved + PlaceCategory.entries.filter { it.name !in known }).toSet()
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
