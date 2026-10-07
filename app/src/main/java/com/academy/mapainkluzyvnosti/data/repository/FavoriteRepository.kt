package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.remote.dto.FavoriteDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

class FavoriteRepository(private val client: SupabaseClient) {

    suspend fun favoritePlaceIds(userId: String): List<String> =
        client.postgrest.from("favorites")
            .select { filter { eq("user_id", userId) } }
            .decodeList<FavoriteDto>()
            .map { it.placeId }

    suspend fun addFavorite(userId: String, placeId: String) {
        client.postgrest.from("favorites").insert(FavoriteDto(userId = userId, placeId = placeId))
    }

    suspend fun removeFavorite(userId: String, placeId: String) {
        client.postgrest.from("favorites").delete {
            filter {
                eq("user_id", userId)
                eq("place_id", placeId)
            }
        }
    }
}
