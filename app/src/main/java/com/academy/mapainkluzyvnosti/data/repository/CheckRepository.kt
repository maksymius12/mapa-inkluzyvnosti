package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.CheckResult
import com.academy.mapainkluzyvnosti.data.remote.dto.toDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

class CheckRepository(private val client: SupabaseClient) {

    suspend fun submitCheck(placeId: String, userId: String, result: CheckResult, comment: String?) {
        client.postgrest.from("checks").insert(result.toDto(placeId, userId, comment))
    }
}
