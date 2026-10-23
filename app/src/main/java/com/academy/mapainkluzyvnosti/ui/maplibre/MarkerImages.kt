package com.academy.mapainkluzyvnosti.ui.maplibre

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import com.academy.mapainkluzyvnosti.data.model.AccessibilityType
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.ui.components.renderBarrierMarkerDrawable
import com.academy.mapainkluzyvnosti.ui.components.renderSosMarkerDrawable
import com.academy.mapainkluzyvnosti.ui.components.renderTypeMarkerDrawable
import com.academy.mapainkluzyvnosti.ui.components.renderUnverifiedCategoryMarkerDrawable
import org.maplibre.android.maps.Style

const val SOS_MARKER_IMAGE_ID = "mapa-sos-marker"
const val BARRIER_MARKER_IMAGE_ID = "mapa-barrier-marker"

/** Мітка за легендою; null — заклад без підтверджених типів доступності. */
fun typeImageId(type: AccessibilityType?): String = "mapa-type-${type?.name ?: "NONE"}"

/** Пунктирна мітка категорії — для доданого користувачем місця, яке ще не перевірили. */
fun unverifiedCategoryImageId(category: PlaceCategory): String = "mapa-unverified-${category.name}"

/** Реєструє растрові мітки легенди, SOS і бар'єра у стилі мапи (ідемпотентно). */
fun Style.registerMarkerImages(context: Context) {
    (AccessibilityType.entries + null).forEach { type ->
        val id = typeImageId(type)
        if (getImage(id) == null) {
            addImage(id, renderTypeMarkerDrawable(context, type) as BitmapDrawable)
        }
    }
    PlaceCategory.entries.forEach { category ->
        val id = unverifiedCategoryImageId(category)
        if (getImage(id) == null) {
            addImage(id, renderUnverifiedCategoryMarkerDrawable(context, category) as BitmapDrawable)
        }
    }
    if (getImage(SOS_MARKER_IMAGE_ID) == null) {
        addImage(SOS_MARKER_IMAGE_ID, renderSosMarkerDrawable(context) as BitmapDrawable)
    }
    if (getImage(BARRIER_MARKER_IMAGE_ID) == null) {
        addImage(BARRIER_MARKER_IMAGE_ID, renderBarrierMarkerDrawable(context) as BitmapDrawable)
    }
}
