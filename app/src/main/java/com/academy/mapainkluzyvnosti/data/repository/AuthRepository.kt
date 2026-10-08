package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.AppUser
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.data.remote.dto.ProfileDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository(private val client: SupabaseClient) {

    private companion object {
        const val SESSION_RESTORE_TIMEOUT_MS = 4000L
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

    suspend fun signUpWithEmail(email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
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

    suspend fun fetchOrCreateProfile(
        userId: String,
        defaultName: String,
        defaultPurposeRole: UserPurposeRole = UserPurposeRole.RESIDENT,
        defaultAgeGroup: UserAgeGroup = UserAgeGroup.ADULT
    ): AppUser {
        val existing = client.postgrest.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<ProfileDto>()

        val dto = existing ?: ProfileDto(
            id = userId,
            name = defaultName,
            purposeRole = defaultPurposeRole,
            ageGroup = defaultAgeGroup
        ).also {
            client.postgrest.from("profiles").insert(it)
        }
        return dto.toDomain()
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
