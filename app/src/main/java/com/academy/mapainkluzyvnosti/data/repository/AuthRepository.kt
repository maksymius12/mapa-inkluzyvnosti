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
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
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
     * Чекає, поки Supabase відновить збережену сесію з диска (supabase-kt зберігає її в SharedPreferences
     * і сам оновлює токен), і каже, чи користувач увійшов. Якщо токен ще оновлюється або немає мережі
     * (Initializing після таймауту / RefreshFailure), але сесія збережена, — користувач лишається
     * увійденим; профіль підтягне SessionSync, щойно оновлення пройде.
     */
    suspend fun awaitRestoredSession(): Boolean {
        withTimeoutOrNull(SESSION_RESTORE_TIMEOUT_MS) {
            client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        }
        return when (client.auth.sessionStatus.value) {
            is SessionStatus.Authenticated -> true
            is SessionStatus.NotAuthenticated -> false
            else -> storedSessionUser() != null
        }
    }

    /** Користувач зі збереженої сесії — доступний навіть до (або без) оновлення токена. */
    suspend fun storedSessionUser(): UserInfo? =
        client.auth.currentUserOrNull() ?: runCatching { client.auth.sessionManager.loadSessionOrNull()?.user }.getOrNull()

    val currentEmail: String? get() = client.auth.currentUserOrNull()?.email

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
        // redirectUrl = null: лист містить 6-значний код ({{ .Token }}), а не посилання з переходом.
        val user = client.auth.signUpWith(Email, redirectUrl = null) {
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

    /** Новий 6-значний код підтвердження реєстрації. */
    suspend fun resendSignUpCode(email: String) {
        client.auth.resendEmail(OtpType.Email.SIGNUP, email, redirectUrl = null)
    }

    /**
     * Лист із 6-значним кодом для скидання пароля. Supabase відповідає однаково
     * незалежно від того, чи існує акаунт, тож наявність пошти не розкривається.
     */
    suspend fun sendPasswordResetCode(email: String) {
        client.auth.resetPasswordForEmail(email, redirectUrl = null)
    }

    /** Підтвердження реєстрації кодом із листа; після успіху сесію створено й збережено. */
    suspend fun verifySignUpCode(email: String, code: String) {
        client.auth.verifyEmailOtp(type = OtpType.Email.SIGNUP, email = email, token = code)
    }

    /** Код скидання пароля; після успіху є тимчасова сесія, щоб встановити новий пароль. */
    suspend fun verifyPasswordResetCode(email: String, code: String) {
        client.auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email, token = code)
    }

    /**
     * Вихід. Без мережі supabase-kt кидає виняток і НЕ очищає локальну сесію — тоді після перезапуску
     * користувач знову виявився б увійденим. Тому локальну сесію прибираємо в будь-якому разі.
     */
    suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (e: Exception) {
            runCatching { client.auth.clearSession() }
        }
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
     * Рядок у profiles створює лише серверний тригер handle_new_user_profile — клієнт туди не пише
     * (INSERT з клієнта відхиляла RLS, 403). Якщо рядка ще немає, профіль береться з метаданих
     * акаунта чи значень за замовчуванням; лише для першого входу через Google без вибору в
     * метаданих просимо обрати мету й вікову категорію.
     */
    suspend fun loadProfileStatus(): ProfileStatus {
        val authUser = client.auth.currentUserOrNull() ?: throw IllegalStateException("No signed-in user")
        val existing = client.postgrest.from("profiles")
            .select { filter { eq("id", authUser.id) } }
            .decodeSingleOrNull<ProfileDto>()
        if (existing != null) {
            return if (existing.rolesConfirmed != false) {
                ProfileStatus.Ready(existing.toDomain(email = authUser.email))
            } else {
                ProfileStatus.NeedsRoles(authUser.id, existing.name ?: suggestedDisplayName() ?: DEFAULT_NAME, profileExists = true)
            }
        }

        val name = suggestedDisplayName() ?: DEFAULT_NAME
        val metadata = authUser.userMetadata
        val metaRole = metadata?.get(META_PURPOSE_ROLE)?.jsonPrimitive?.contentOrNull?.let(::purposeRoleFromApi)
        val metaAge = metadata?.get(META_AGE_GROUP)?.jsonPrimitive?.contentOrNull?.let(::ageGroupFromApi)
        if (metaRole == null && metaAge == null && authUser.isGoogleUser()) {
            return ProfileStatus.NeedsRoles(authUser.id, name, profileExists = false)
        }
        // Рядка немає (тригер/backfill ще не виконано): не ламаємось, показуємо підставлені значення
        // й один раз пропонуємо перевірити мету й вік у Налаштуваннях.
        return ProfileStatus.Ready(
            AppUser(
                id = authUser.id,
                name = name,
                purposeRole = metaRole ?: UserPurposeRole.RESIDENT,
                ageGroup = metaAge ?: UserAgeGroup.ADULT,
                email = authUser.email,
                rolesNeedReview = metaRole == null || metaAge == null
            )
        )
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
            updated.firstOrNull()?.let { return it.toDomain(email = currentEmail) }
        }
        // Рядка в profiles немає — зберігаємо вибір у метадані акаунта (не таблиця, RLS не діє);
        // loadProfileStatus прочитає їх при наступному вході й більше не питатиме.
        client.auth.updateUser {
            data = buildJsonObject {
                put(META_PURPOSE_ROLE, purposeRole.apiValue)
                put(META_AGE_GROUP, ageGroup.apiValue)
            }
        }
        return AppUser(id = status.userId, name = status.name, purposeRole = purposeRole, ageGroup = ageGroup, email = currentEmail)
    }

    private fun UserInfo.isGoogleUser(): Boolean {
        val provider = appMetadata?.get("provider")?.jsonPrimitive?.contentOrNull
        val providers = (appMetadata?.get("providers") as? JsonArray)?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        return provider == "google" || "google" in providers || identities?.any { it.provider == "google" } == true
    }

    /**
     * Зміна мети й вікової категорії в Налаштуваннях. Також знімає позначку roles_defaulted.
     * Якщо рядка в profiles немає (або колонок ще не додано міграцією), вибір зберігається
     * в метаданих акаунта — loadProfileStatus прочитає його при наступному вході.
     */
    suspend fun updateProfileRoles(userId: String, purposeRole: UserPurposeRole, ageGroup: UserAgeGroup) {
        suspend fun update(withDefaultedFlag: Boolean) = client.postgrest.from("profiles").update({
            set("purpose_role", purposeRole)
            set("age_group", ageGroup)
            if (withDefaultedFlag) set("roles_defaulted", false)
        }) {
            select()
            filter { eq("id", userId) }
        }.decodeList<ProfileDto>()

        val updated = try {
            update(withDefaultedFlag = true)
        } catch (e: PostgrestRestException) {
            // PGRST204 — колонки roles_defaulted ще немає (міграцію 20261027 не виконано).
            if (e.code == "PGRST204") update(withDefaultedFlag = false) else throw e
        }
        if (updated.isEmpty()) {
            client.auth.updateUser {
                data = buildJsonObject {
                    put(META_PURPOSE_ROLE, purposeRole.apiValue)
                    put(META_AGE_GROUP, ageGroup.apiValue)
                }
            }
        }
    }

    /** Інструктаж переглянуто — щоб не показувати після перевстановлення. Помилки не критичні. */
    suspend fun markOnboardingDone(userId: String) {
        runCatching {
            client.postgrest.from("profiles").update({ set("onboarding_done", true) }) { filter { eq("id", userId) } }
        }
    }

    private fun ProfileDto.toDomain(email: String?) = AppUser(
        id = id,
        name = name?.takeIf { it.isNotBlank() } ?: suggestedDisplayName() ?: DEFAULT_NAME,
        purposeRole = purposeRole,
        ageGroup = ageGroup,
        points = points ?: 0,
        checksCount = checksCount ?: 0,
        email = email,
        rolesNeedReview = rolesDefaulted == true,
        onboardingDone = onboardingDone == true
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
