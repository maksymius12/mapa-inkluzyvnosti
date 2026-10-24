package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.AppUser
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.data.remote.dto.ProfileDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Результат реєстрації поштою. Відсутність сесії — не помилка, а очікування підтвердження пошти. */
sealed interface SignUpResult {
    /** Підтвердження пошти вимкнене — сесія вже є. */
    data object SignedIn : SignUpResult

    /** Supabase надіслав лист; увійти можна після переходу за посиланням. */
    data class ConfirmationRequired(val email: String) : SignUpResult

    /**
     * Пошта вже зареєстрована. З увімкненим «Confirm email» Supabase не повертає помилку
     * (захист від перебору адрес), а віддає користувача з порожнім списком identities.
     */
    data object AlreadyRegistered : SignUpResult
}

/** Стан профілю після входу. */
sealed interface ProfileStatus {
    data class Ready(val user: AppUser) : ProfileStatus

    /** Мету й вікову категорію ще не обрано (перший вхід через Google) — показати крок вибору. */
    data class NeedsRoles(val userId: String, val name: String, val profileExists: Boolean) : ProfileStatus
}

class AuthRepository(private val client: SupabaseClient) {

    private companion object {
        const val SESSION_RESTORE_TIMEOUT_MS = 4000L
        const val DEFAULT_NAME = "Користувач"

        // Ключі user metadata при реєстрації; їх читає тригер handle_new_user_profile.
        const val META_NAME = "name"
        const val META_PURPOSE_ROLE = "purpose_role"
        const val META_AGE_GROUP = "age_group"
    }

    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    val currentUserId: String? get() = client.auth.currentUserOrNull()?.id

    /**
     * Чекає, поки Supabase відновить збережену сесію з диска, і каже, чи користувач уже ввійшов.
     * Без мережі сесія лишається чинною локально, тож таймаут трактуємо як «немає сесії».
     */
    suspend fun awaitRestoredSession(): Boolean {
        withTimeoutOrNull(SESSION_RESTORE_TIMEOUT_MS) {
            client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        }
        return currentUserId != null
    }

    /** Ім'я для відображення з реального акаунта: ПІБ із Google, інакше локальна частина email. */
    fun suggestedDisplayName(): String? {
        val user = client.auth.currentUserOrNull() ?: return null
        val metadata = user.userMetadata
        val fromMetadata = listOf("full_name", "name")
            .firstNotNullOfOrNull { key -> metadata?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } }
        return fromMetadata ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
    }

    fun currentAvatarUrl(): String? =
        client.auth.currentUserOrNull()?.userMetadata
            ?.let { metadata ->
                listOf("avatar_url", "picture").firstNotNullOfOrNull { key ->
                    metadata[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                }
            }

    suspend fun signInWithGoogleIdToken(idToken: String) {
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
        }
    }

    suspend fun signInWithEmail(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /**
     * Реєстрація без жодного запису в базу з клієнта: ім'я, мета й вікова категорія йдуть
     * у user metadata, профіль створює серверний тригер. Тому відсутність сесії
     * (увімкнене «Confirm email») більше не призводить до помилки.
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        name: String,
        purposeRole: UserPurposeRole,
        ageGroup: UserAgeGroup
    ): SignUpResult {
        val user = client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            this.data = buildJsonObject {
                put(META_NAME, name)
                put(META_PURPOSE_ROLE, purposeRole.apiValue)
                put(META_AGE_GROUP, ageGroup.apiValue)
            }
        }
        val session = client.auth.currentSessionOrNull()
        return when {
            session != null && (user == null || session.user?.id == null || session.user?.id == user.id) -> SignUpResult.SignedIn
            user != null && user.identities?.isEmpty() == true -> SignUpResult.AlreadyRegistered
            else -> SignUpResult.ConfirmationRequired(email)
        }
    }

    suspend fun resendSignUpConfirmation(email: String) {
        client.auth.resendEmail(OtpType.Email.SIGNUP, email)
    }

    suspend fun resetPasswordForEmail(email: String) {
        client.auth.resetPasswordForEmail(email)
    }

    suspend fun signOut() {
        client.auth.signOut()
    }

    suspend fun updatePassword(newPassword: String) {
        client.auth.updateUser { password = newPassword }
    }

    /** Викликає Edge Function "delete-account" (service-role admin API) — клієнтський SDK не може видалити користувача сам. */
    suspend fun deleteAccount() {
        client.functions.invoke("delete-account")
    }

    /**
     * Профіль поточного користувача після входу. Нічого не питає в тих, хто вже має профіль.
     * Якщо профілю немає (тригер ще не розгорнуто або акаунт старий), створює його з метаданих
     * реєстрації чи зі значеннями за замовчуванням; лише для першого входу через Google
     * просить обрати мету й вікову категорію.
     */
    suspend fun loadProfileStatus(): ProfileStatus {
        val authUser = client.auth.currentUserOrNull() ?: throw IllegalStateException("No signed-in user")
        val existing = client.postgrest.from("profiles")
            .select { filter { eq("id", authUser.id) } }
            .decodeSingleOrNull<ProfileDto>()
        if (existing != null) {
            return if (existing.rolesConfirmed) {
                ProfileStatus.Ready(existing.toDomain())
            } else {
                ProfileStatus.NeedsRoles(authUser.id, existing.name, profileExists = true)
            }
        }

        val name = suggestedDisplayName() ?: DEFAULT_NAME
        val metadata = authUser.userMetadata
        val metaRole = metadata?.get(META_PURPOSE_ROLE)?.jsonPrimitive?.contentOrNull?.let(::purposeRoleFromApi)
        val metaAge = metadata?.get(META_AGE_GROUP)?.jsonPrimitive?.contentOrNull?.let(::ageGroupFromApi)
        if (metaRole == null && metaAge == null && authUser.isGoogleUser()) {
            return ProfileStatus.NeedsRoles(authUser.id, name, profileExists = false)
        }
        val dto = ProfileDto(
            id = authUser.id,
            name = name,
            purposeRole = metaRole ?: UserPurposeRole.RESIDENT,
            ageGroup = metaAge ?: UserAgeGroup.ADULT
        )
        client.postgrest.from("profiles").insert(dto)
        return ProfileStatus.Ready(dto.toDomain())
    }

    /** Зберігає вибір мети й вікової категорії з кроку 2 (перший вхід через Google). */
    suspend fun confirmProfileRoles(status: ProfileStatus.NeedsRoles, purposeRole: UserPurposeRole, ageGroup: UserAgeGroup): AppUser {
        if (status.profileExists) {
            val updated = client.postgrest.from("profiles").update({
                set("purpose_role", purposeRole)
                set("age_group", ageGroup)
                set("roles_confirmed", true)
            }) {
                select()
                filter { eq("id", status.userId) }
            }.decodeList<ProfileDto>()
            updated.firstOrNull()?.let { return it.toDomain() }
        }
        val dto = ProfileDto(id = status.userId, name = status.name, purposeRole = purposeRole, ageGroup = ageGroup)
        client.postgrest.from("profiles").insert(dto)
        return dto.toDomain()
    }

    private fun UserInfo.isGoogleUser(): Boolean {
        val provider = appMetadata?.get("provider")?.jsonPrimitive?.contentOrNull
        val providers = (appMetadata?.get("providers") as? JsonArray)?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        return provider == "google" || "google" in providers || identities?.any { it.provider == "google" } == true
    }

    suspend fun updateProfileRoles(userId: String, purposeRole: UserPurposeRole, ageGroup: UserAgeGroup) {
        client.postgrest.from("profiles").update({
            set("purpose_role", purposeRole)
            set("age_group", ageGroup)
        }) { filter { eq("id", userId) } }
    }

    suspend fun incrementStudentProgress(userId: String, pointsDelta: Int) {
        client.postgrest.rpc(
            "increment_profile_progress",
            buildJsonObject {
                put("p_user_id", userId)
                put("p_points_delta", pointsDelta)
            }
        )
    }

    private fun ProfileDto.toDomain() = AppUser(
        id = id,
        name = name,
        purposeRole = purposeRole,
        ageGroup = ageGroup,
        points = points,
        checksCount = checksCount
    )
}

/** Значення колонки profiles.purpose_role / ключа metadata. */
val UserPurposeRole.apiValue: String
    get() = when (this) {
        UserPurposeRole.RESIDENT -> "resident"
        UserPurposeRole.VOLUNTEER -> "volunteer"
    }

/** Значення колонки profiles.age_group / ключа metadata. */
val UserAgeGroup.apiValue: String
    get() = when (this) {
        UserAgeGroup.STUDENT -> "student"
        UserAgeGroup.ADULT -> "adult"
    }

private fun purposeRoleFromApi(value: String): UserPurposeRole? = UserPurposeRole.entries.firstOrNull { it.apiValue == value }

private fun ageGroupFromApi(value: String): UserAgeGroup? = UserAgeGroup.entries.firstOrNull { it.apiValue == value }
