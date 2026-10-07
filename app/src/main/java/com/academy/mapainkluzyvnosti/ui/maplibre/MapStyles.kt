package com.academy.mapainkluzyvnosti.ui.maplibre

/** Векторні стилі OpenFreeMap (безкоштовно, без API-ключа). */
object MapStyles {
    const val LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
    const val DARK = "https://tiles.openfreemap.org/styles/dark"

    fun forTheme(isDarkTheme: Boolean): String = if (isDarkTheme) DARK else LIBERTY
}
