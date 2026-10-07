package com.academy.mapainkluzyvnosti.data.local

import android.content.Context
import com.academy.mapainkluzyvnosti.data.model.Route
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Обрані маршрути мають жити довше за процес застосунку: ранкову перевірку
 * (WorkManager, [com.academy.mapainkluzyvnosti.work.RouteBarrierCheckWorker])
 * може запустити система в окремому виклику процесу, коли UI ще не піднятий,
 * тож стан читається напряму з SharedPreferences, а не з in-memory стору.
 */
object SavedRoutesStorage {

    private const val PREFS_NAME = "saved_routes"
    private const val KEY_ROUTES = "routes_json"

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Route.serializer())

    fun load(context: Context): List<Route> {
        val raw = prefs(context).getString(KEY_ROUTES, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    fun save(context: Context, routes: List<Route>) {
        prefs(context).edit().putString(KEY_ROUTES, json.encodeToString(serializer, routes)).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
