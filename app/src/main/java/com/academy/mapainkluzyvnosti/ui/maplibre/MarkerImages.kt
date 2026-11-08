package com.academy.mapainkluzyvnosti.ui.maplibre

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.ui.components.renderBarrierMarkerDrawable
import com.academy.mapainkluzyvnosti.ui.components.renderPlaceMarkerDrawable
import org.maplibre.android.maps.Style

const val BARRIER_MARKER_IMAGE_ID = "mapa-barrier-marker"

/** Мітка місця: значок категорії + бейдж статусу доступності в куті. */
fun placeImageId(category: PlaceCategory, status: AccessStatus): String = "mapa-place-${category.name}-${status.name}"

/** Реєструє растрові мітки місць (категорія × статус) і бар'єра у стилі мапи (ідемпотентно). */
fun Style.registerMarkerImages(context: Context) {
    PlaceCategory.entries.forEach { category ->
        AccessStatus.entries.forEach { status ->
            val id = placeImageId(category, status)
            if (getImage(id) == null) {
                addImage(id, renderPlaceMarkerDrawable(context, category, status) as BitmapDrawable)
            }
        }
    }
    if (getImage(BARRIER_MARKER_IMAGE_ID) == null) {
        addImage(BARRIER_MARKER_IMAGE_ID, renderBarrierMarkerDrawable(context) as BitmapDrawable)
    }
}
