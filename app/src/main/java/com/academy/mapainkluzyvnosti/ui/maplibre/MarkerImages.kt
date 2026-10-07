package com.academy.mapainkluzyvnosti.ui.maplibre

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.ui.components.renderPlaceMarkerDrawable
import com.academy.mapainkluzyvnosti.ui.components.renderSosMarkerDrawable
import org.maplibre.android.maps.Style

const val SOS_MARKER_IMAGE_ID = "mapa-sos-marker"

fun placeImageId(category: PlaceCategory, status: AccessStatus): String = "mapa-place-${category.name}-${status.name}"

/** Реєструє растрові іконки категорій/статусів місць і SOS у стилі мапи (ідемпотентно). */
fun Style.registerMarkerImages(context: Context) {
    PlaceCategory.entries.forEach { category ->
        AccessStatus.entries.forEach { status ->
            val id = placeImageId(category, status)
            if (getImage(id) == null) {
                addImage(id, renderPlaceMarkerDrawable(context, category, status) as BitmapDrawable)
            }
        }
    }
    if (getImage(SOS_MARKER_IMAGE_ID) == null) {
        addImage(SOS_MARKER_IMAGE_ID, renderSosMarkerDrawable(context) as BitmapDrawable)
    }
}
