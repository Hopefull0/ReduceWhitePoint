package com.whitepoint.reducer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Crisp 24x24 Lucide-style stroke icons (https://lucide.dev/).
 */
object LucideIcons {

    private fun lucide(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    private fun ImageVector.Builder.strokePath(pathData: ImageVector.Builder.() -> Unit) {
        // Helper placeholder; individual paths use explicit path() below for clarity
    }

    private val stroke = SolidColor(Color.White)

    val Sun: ImageVector by lazy {
        lucide("Sun") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 8f)
                arcToRelative(4f, 4f, 0f, true, false, 0f, 8f)
                arcToRelative(4f, 4f, 0f, true, false, 0f, -8f)
                moveTo(12f, 2f); lineTo(12f, 4f)
                moveTo(12f, 20f); lineTo(12f, 22f)
                moveTo(4.93f, 4.93f); lineTo(6.34f, 6.34f)
                moveTo(17.66f, 17.66f); lineTo(19.07f, 19.07f)
                moveTo(2f, 12f); lineTo(4f, 12f)
                moveTo(20f, 12f); lineTo(22f, 12f)
                moveTo(6.34f, 17.66f); lineTo(4.93f, 19.07f)
                moveTo(19.07f, 4.93f); lineTo(17.66f, 6.34f)
            }
        }
    }

    val Moon: ImageVector by lazy {
        lucide("Moon") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 3f)
                arcToRelative(6f, 6f, 0f, false, false, 9f, 9f)
                arcToRelative(9f, 9f, 0f, true, true, -9f, -9f)
                close()
            }
        }
    }

    val Sunrise: ImageVector by lazy {
        lucide("Sunrise") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2f); lineTo(12f, 8f)
                moveTo(4.93f, 10.93f); lineTo(6.34f, 12.34f)
                moveTo(2f, 18f); lineTo(4f, 18f)
                moveTo(20f, 18f); lineTo(22f, 18f)
                moveTo(19.07f, 10.93f); lineTo(17.66f, 12.34f)
                moveTo(22f, 22f); lineTo(2f, 22f)
                moveTo(8f, 6f); lineTo(12f, 2f); lineTo(16f, 6f)
                moveTo(16f, 18f); arcToRelative(4f, 4f, 0f, false, false, -8f, 0f)
            }
        }
    }

    val Clock: ImageVector by lazy {
        lucide("Clock") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2f)
                arcToRelative(10f, 10f, 0f, true, false, 0f, 20f)
                arcToRelative(10f, 10f, 0f, true, false, 0f, -20f)
                moveTo(12f, 6f); lineTo(12f, 12f); lineTo(16f, 14f)
            }
        }
    }

    val SlidersHorizontal: ImageVector by lazy {
        lucide("SlidersHorizontal") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21f, 4f); lineTo(14f, 4f)
                moveTo(10f, 4f); lineTo(3f, 4f)
                moveTo(21f, 12f); lineTo(12f, 12f)
                moveTo(8f, 12f); lineTo(3f, 12f)
                moveTo(21f, 20f); lineTo(16f, 20f)
                moveTo(12f, 20f); lineTo(3f, 20f)
                moveTo(14f, 2f); lineTo(14f, 6f)
                moveTo(8f, 10f); lineTo(8f, 14f)
                moveTo(16f, 18f); lineTo(16f, 22f)
            }
        }
    }

    val Sparkles: ImageVector by lazy {
        lucide("Sparkles") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9.937f, 15.5f)
                arcToRelative(2f, 2f, 0f, false, false, -1.437f, -1.437f)
                lineTo(3f, 12.5f)
                lineTo(8.5f, 10.937f)
                arcToRelative(2f, 2f, 0f, false, false, 1.437f, -1.437f)
                lineTo(11.5f, 4f)
                lineTo(13.063f, 9.5f)
                arcToRelative(2f, 2f, 0f, false, false, 1.437f, 1.437f)
                lineTo(20f, 12.5f)
                lineTo(14.5f, 14.063f)
                arcToRelative(2f, 2f, 0f, false, false, -1.437f, 1.437f)
                lineTo(11.5f, 21f)
                close()
                moveTo(20f, 3f); lineTo(20f, 7f)
                moveTo(22f, 5f); lineTo(18f, 5f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        lucide("Plus") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 12f); lineTo(19f, 12f)
                moveTo(12f, 5f); lineTo(12f, 19f)
            }
        }
    }

    val Trash2: ImageVector by lazy {
        lucide("Trash2") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 6f); lineTo(21f, 6f)
                moveTo(19f, 6f); lineTo(19f, 20f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                lineTo(7f, 22f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                lineTo(5f, 6f)
                moveTo(8f, 6f); lineTo(8f, 4f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                lineTo(14f, 2f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                lineTo(16f, 6f)
                moveTo(10f, 11f); lineTo(10f, 17f)
                moveTo(14f, 11f); lineTo(14f, 17f)
            }
        }
    }

    val ShieldAlert: ImageVector by lazy {
        lucide("ShieldAlert") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 13f)
                curveTo(20f, 18f, 16.5f, 20.5f, 12.34f, 21.95f)
                arcToRelative(1f, 1f, 0f, false, true, -0.68f, 0f)
                curveTo(7.5f, 20.5f, 4f, 18f, 4f, 13f)
                verticalLineTo(6f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
                curveTo(7f, 5f, 9.5f, 3.8f, 11.24f, 2.28f)
                arcToRelative(1.17f, 1.17f, 0f, false, true, 1.52f, 0f)
                curveTo(14.51f, 3.81f, 17f, 5f, 19f, 5f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
                close()
                moveTo(12f, 8f); lineTo(12f, 12f)
                moveTo(12f, 16f); lineTo(12.01f, 16f)
            }
        }
    }

    val Lock: ImageVector by lazy {
        lucide("Lock") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 11f)
                horizontalLineTo(19f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineTo(20f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(13f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
                moveTo(7f, 11f); verticalLineTo(7f)
                arcToRelative(5f, 5f, 0f, false, true, 10f, 0f)
                verticalLineTo(11f)
            }
        }
    }

    val LayoutGrid: ImageVector by lazy {
        lucide("LayoutGrid") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 3f); horizontalLineTo(9f); verticalLineTo(9f); horizontalLineTo(4f); close()
                moveTo(15f, 3f); horizontalLineTo(20f); verticalLineTo(9f); horizontalLineTo(15f); close()
                moveTo(15f, 15f); horizontalLineTo(20f); verticalLineTo(21f); horizontalLineTo(15f); close()
                moveTo(4f, 15f); horizontalLineTo(9f); verticalLineTo(21f); horizontalLineTo(4f); close()
            }
        }
    }

    val Terminal: ImageVector by lazy {
        lucide("Terminal") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 17f); lineTo(10f, 11f); lineTo(4f, 5f)
                moveTo(12f, 19f); lineTo(20f, 19f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        lucide("Copy") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 8f); horizontalLineTo(20f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineTo(20f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(10f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(10f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
                moveTo(4f, 16f)
                curveTo(2.9f, 16f, 2f, 15.1f, 2f, 14f)
                verticalLineTo(4f)
                curveTo(2f, 2.9f, 2.9f, 2f, 4f, 2f)
                horizontalLineTo(14f)
                curveTo(15.1f, 2f, 16f, 2.9f, 16f, 4f)
            }
        }
    }

    val Check: ImageVector by lazy {
        lucide("Check") {
            path(
                stroke = stroke,
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 6f); lineTo(9f, 17f); lineTo(4f, 12f)
            }
        }
    }

    val RotateCcw: ImageVector by lazy {
        lucide("RotateCcw") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 12f)
                arcToRelative(9f, 9f, 0f, true, false, 9f, -9f)
                arcToRelative(9.75f, 9.75f, 0f, false, false, -6.74f, 2.74f)
                lineTo(3f, 8f)
                moveTo(3f, 3f); verticalLineTo(8f); horizontalLineTo(8f)
            }
        }
    }

    val MapPin: ImageVector by lazy {
        lucide("MapPin") {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 10f)
                curveTo(20f, 14.993f, 14.461f, 20.193f, 12.601f, 21.799f)
                arcToRelative(1f, 1f, 0f, false, true, -1.202f, 0f)
                curveTo(9.539f, 20.193f, 4f, 14.993f, 4f, 10f)
                arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
                close()
                moveTo(12f, 7f)
                arcToRelative(3f, 3f, 0f, true, false, 0f, 6f)
                arcToRelative(3f, 3f, 0f, true, false, 0f, -6f)
            }
        }
    }
}
