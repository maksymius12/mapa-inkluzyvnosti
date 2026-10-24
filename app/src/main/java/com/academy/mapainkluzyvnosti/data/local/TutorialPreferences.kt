package com.academy.mapainkluzyvnosti.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.tutorialDataStore: DataStore<Preferences> by preferencesDataStore(name = "tutorial")

/** Локальні прапорці інструктажу «Як користуватися» й одноразових підказок (DataStore). */
class TutorialPreferences(private val context: Context) {

    private val fullSeenKey = booleanPreferencesKey("tutorial_full_seen")
    private val guestSeenKey = booleanPreferencesKey("tutorial_guest_seen")

    private fun rolesPromptKey(userId: String) = booleanPreferencesKey("roles_prompt_shown_$userId")

    private suspend fun read(): Preferences =
        context.tutorialDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()

    suspend fun isTutorialSeen(guest: Boolean): Boolean = read()[if (guest) guestSeenKey else fullSeenKey] == true

    suspend fun markTutorialSeen(guest: Boolean) {
        context.tutorialDataStore.edit { it[if (guest) guestSeenKey else fullSeenKey] = true }
    }

    suspend fun isRolesPromptShown(userId: String): Boolean = read()[rolesPromptKey(userId)] == true

    suspend fun markRolesPromptShown(userId: String) {
        context.tutorialDataStore.edit { it[rolesPromptKey(userId)] = true }
    }
}
