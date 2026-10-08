package com.academy.mapainkluzyvnosti.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath

/**
 * Малює Compose [ImageVector] (Material-іконку) на Android-канвасі — MapLibre приймає лише
 * растрові зображення маркерів, а ComposeView поза композицією тут зайвий. Підтримані прості
 * іконки з плоскою структурою контурів (усі Material-іконки саме такі).
 */
fun ImageVector.drawOnCanvas(canvas: Canvas, left: Float, top: Float, sizePx: Float, color: Color) {
    val paths = mutableListOf<VectorPath>()
    fun collect(node: VectorNode) {
        when (node) {
            // Службові контури без заливки чи з нульовою прозорістю (рамка 24x24) не малюємо.
            is VectorPath -> if (node.fill != null && node.fillAlpha > 0f) paths += node
            is VectorGroup -> node.forEach { collect(it) }
        }
    }
    collect(root)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color.toArgb()
    }
    val scale = sizePx / viewportWidth
    canvas.save()
    canvas.translate(left, top)
    canvas.scale(scale, scale)
    paths.forEach { vectorPath ->
        val path = PathParser().addPathNodes(vectorPath.pathData).toPath().asAndroidPath()
        path.fillType = if (vectorPath.pathFillType == PathFillType.EvenOdd) {
            android.graphics.Path.FillType.EVEN_ODD
        } else {
            android.graphics.Path.FillType.WINDING
        }
        canvas.drawPath(path, paint)
    }
    canvas.restore()
}
