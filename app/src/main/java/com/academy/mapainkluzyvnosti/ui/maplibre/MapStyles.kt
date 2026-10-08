package com.academy.mapainkluzyvnosti.ui.maplibre

/** Векторні стилі OpenFreeMap (безкоштовно, без API-ключа). */
object MapStyles {
    const val LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
    const val DARK = "https://tiles.openfreemap.org/styles/dark"

    /**
     * За макетом мапа світла і в темному інтерфейсі: кольорові мітки легенди на ній читаються
     * найкраще. Темний стиль лишається доступним як [DARK].
     */
    @Suppress("UNUSED_PARAMETER")
    fun forTheme(isDarkTheme: Boolean): String = LIBERTY
}
