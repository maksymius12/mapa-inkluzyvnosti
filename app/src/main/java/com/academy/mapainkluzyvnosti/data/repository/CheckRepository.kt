package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.CheckResult
import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import com.academy.mapainkluzyvnosti.data.remote.dto.toDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

class CheckRepository(private val client: SupabaseClient) {

    /**
     * Перевірка за 7 критеріями: лише INSERT у checks. Статус місця, паркування й бали учня
     * перераховує серверний тригер checks_after_insert_apply (міграція 20261028120000).
     */
    suspend fun submitCheck(placeId: String, userId: String, result: CheckResult, comment: String?) {
        val dto = result.toDto(placeId, userId, comment)
        try {
            client.postgrest.from("checks").insert(dto)
        } catch (e: Exception) {
            SupabaseErrorLog.log(
                "POST /rest/v1/checks",
                e,
                mapOf(
                    "place_id" to placeId,
                    "user_id_is_set" to userId.isNotBlank(),
                    "ramp" to dto.ramp, "door_width" to dto.doorWidth, "threshold" to dto.threshold,
                    "elevator" to dto.elevator, "toilet" to dto.toilet, "tactile" to dto.tactile,
                    "staff_assistance" to dto.staffAssistance, "accessible_parking" to dto.accessibleParking,
                    "comment_length" to (comment?.length ?: 0)
                )
            )
            throw e
        }
    }
}
