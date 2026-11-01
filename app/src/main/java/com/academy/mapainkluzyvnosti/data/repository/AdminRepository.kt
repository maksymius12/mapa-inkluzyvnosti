package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.data.model.UserRole
import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/** Користувач у списку адмін-панелі (з auth.users + profiles). */
data class AdminUser(
    val id: String,
    val email: String?,
    val name: String?,
    val role: UserRole,
    val purposeRole: UserPurposeRole?,
    val ageGroup: UserAgeGroup?
)

@Serializable
private data class AdminUserDto(
    val id: String,
    val email: String? = null,
    val name: String? = null,
    val role: UserRole = UserRole.USER,
    @SerialName("purpose_role") val purposeRole: UserPurposeRole? = null,
    @SerialName("age_group") val ageGroup: UserAgeGroup? = null
)

/** Метрики адмін-панелі за один місяць (RPC admin_monthly_stats, міграція 20261101120000). */
@Serializable
data class MonthlyStats(
    @SerialName("new_users") val newUsers: Int = 0,
    @SerialName("requests_total") val requestsTotal: Int = 0,
    @SerialName("requests_approved") val requestsApproved: Int = 0,
    @SerialName("requests_rejected") val requestsRejected: Int = 0,
    @SerialName("new_reviews") val newReviews: Int = 0,
    @SerialName("existence_reports_total") val existenceReportsTotal: Int = 0,
    @SerialName("existence_reports_approved") val existenceReportsApproved: Int = 0
)

/**
 * Адмін-панель: список користувачів і зміна ролі/мети. Права перевіряє сервер
 * (RPC admin_* з міграції 20261027160000 викликають is_admin() самі).
 */
class AdminRepository(private val client: SupabaseClient) {

    suspend fun listUsers(search: String?): List<AdminUser> =
        try {
            client.postgrest.rpc(
                "admin_list_users",
                buildJsonObject { search?.trim()?.takeIf { it.isNotEmpty() }?.let { put("p_search", it) } }
            ).decodeList<AdminUserDto>().map { it.toDomain() }
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/rpc/admin_list_users", e, mapOf("has_search" to !search.isNullOrBlank()))
            throw e
        }

    suspend fun setRole(userId: String, role: UserRole) {
        try {
            client.postgrest.rpc(
                "admin_set_user_role",
                buildJsonObject {
                    put("p_user_id", userId)
                    put("p_role", role.apiValue)
                }
            )
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/rpc/admin_set_user_role", e, mapOf("p_user_id" to userId, "p_role" to role.apiValue))
            throw e
        }
    }

    suspend fun setPurpose(userId: String, purposeRole: UserPurposeRole) {
        try {
            client.postgrest.rpc(
                "admin_set_user_purpose",
                buildJsonObject {
                    put("p_user_id", userId)
                    put("p_purpose", purposeRole.apiValue)
                }
            )
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/rpc/admin_set_user_purpose", e, mapOf("p_user_id" to userId, "p_purpose" to purposeRole.apiValue))
            throw e
        }
    }

    /** [month] — будь-який день потрібного місяця. */
    suspend fun monthlyStats(month: LocalDate): MonthlyStats =
        try {
            client.postgrest.rpc("admin_monthly_stats", buildJsonObject { put("p_month", month.toString()) })
                .decodeList<MonthlyStats>().firstOrNull() ?: MonthlyStats()
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/rpc/admin_monthly_stats", e, mapOf("p_month" to month))
            throw e
        }

    private fun AdminUserDto.toDomain() = AdminUser(
        id = id,
        email = email,
        name = name,
        role = role,
        purposeRole = purposeRole,
        ageGroup = ageGroup
    )
}

/** Значення колонки profiles.role. */
val UserRole.apiValue: String
    get() = when (this) {
        UserRole.USER -> "user"
        UserRole.ADMIN -> "admin"
    }
