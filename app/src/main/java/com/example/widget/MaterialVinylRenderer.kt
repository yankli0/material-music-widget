package com.example.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.EmptyVinylStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Resolves live Material You / Material 3 tonal color tokens for both light & dark themes
 * so procedural vinyl discs and widget buttons match the user's Android dynamic wallpaper palette.
 */
data class MaterialVinylPalette(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val surface: Int,
    val surfaceContainerLow: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val outlineVariant: Int
) {
    companion object {
        fun fromContext(context: Context): MaterialVinylPalette {
            val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                fun sys(resId: Int): Int = ContextCompat.getColor(context, resId)
                return if (isDark) {
                    MaterialVinylPalette(
                        primary = sys(android.R.color.system_accent1_200),
                        onPrimary = sys(android.R.color.system_accent1_800),
                        primaryContainer = sys(android.R.color.system_accent1_700),
                        onPrimaryContainer = sys(android.R.color.system_accent1_100),
                        secondary = sys(android.R.color.system_accent2_200),
                        secondaryContainer = sys(android.R.color.system_accent2_700),
                        onSecondaryContainer = sys(android.R.color.system_accent2_100),
                        tertiary = sys(android.R.color.system_accent3_200),
                        tertiaryContainer = sys(android.R.color.system_accent3_700),
                        onTertiaryContainer = sys(android.R.color.system_accent3_100),
                        surface = sys(android.R.color.system_neutral1_900),
                        surfaceContainerLow = sys(android.R.color.system_neutral1_900),
                        surfaceContainerHigh = sys(android.R.color.system_neutral1_800),
                        surfaceContainerHighest = sys(android.R.color.system_neutral2_800),
                        outlineVariant = sys(android.R.color.system_neutral2_600)
                    )
                } else {
                    MaterialVinylPalette(
                        primary = sys(android.R.color.system_accent1_600),
                        onPrimary = sys(android.R.color.system_neutral1_0),
                        primaryContainer = sys(android.R.color.system_accent1_100),
                        onPrimaryContainer = sys(android.R.color.system_accent1_900),
                        secondary = sys(android.R.color.system_accent2_600),
                        secondaryContainer = sys(android.R.color.system_accent2_100),
                        onSecondaryContainer = sys(android.R.color.system_accent2_900),
                        tertiary = sys(android.R.color.system_accent3_600),
                        tertiaryContainer = sys(android.R.color.system_accent3_100),
                        onTertiaryContainer = sys(android.R.color.system_accent3_900),
                        surface = sys(android.R.color.system_neutral1_10),
                        surfaceContainerLow = sys(android.R.color.system_neutral1_50),
                        surfaceContainerHigh = sys(android.R.color.system_neutral2_100),
                        surfaceContainerHighest = sys(android.R.color.system_neutral2_200),
                        outlineVariant = sys(android.R.color.system_neutral2_300)
                    )
                }
            }

            // Material 3 tonal fallback for API < 31
            return if (isDark) {
                MaterialVinylPalette(
                    primary = 0xFFD0BCFF.toInt(),
                    onPrimary = 0xFF381E72.toInt(),
                    primaryContainer = 0xFF4F378B.toInt(),
                    onPrimaryContainer = 0xFFEADDFF.toInt(),
                    secondary = 0xFFCCC2DC.toInt(),
                    secondaryContainer = 0xFF4A4458.toInt(),
                    onSecondaryContainer = 0xFFE8DEF8.toInt(),
                    tertiary = 0xFFEFB8C8.toInt(),
                    tertiaryContainer = 0xFF633B48.toInt(),
                    onTertiaryContainer = 0xFFFFD8E4.toInt(),
                    surface = 0xFF141218.toInt(),
                    surfaceContainerLow = 0xFF1D1B20.toInt(),
                    surfaceContainerHigh = 0xFF2B2930.toInt(),
                    surfaceContainerHighest = 0xFF36343B.toInt(),
                    outlineVariant = 0xFF49454F.toInt()
                )
            } else {
                MaterialVinylPalette(
                    primary = 0xFF6750A4.toInt(),
                    onPrimary = 0xFFFFFFFF.toInt(),
                    primaryContainer = 0xFFEADDFF.toInt(),
                    onPrimaryContainer = 0xFF21005D.toInt(),
                    secondary = 0xFF625B71.toInt(),
                    secondaryContainer = 0xFFE8DEF8.toInt(),
                    onSecondaryContainer = 0xFF1D192B.toInt(),
                    tertiary = 0xFF7D5260.toInt(),
                    tertiaryContainer = 0xFFFFD8E4.toInt(),
                    onTertiaryContainer = 0xFF31111D.toInt(),
                    surface = 0xFFFEF7FF.toInt(),
                    surfaceContainerLow = 0xFFF7F2FA.toInt(),
                    surfaceContainerHigh = 0xFFECE6F0.toInt(),
                    surfaceContainerHighest = 0xFFE6E0E9.toInt(),
                    outlineVariant = 0xFFCAC4D0.toInt()
                )
            }
        }
    }
}

/**
 * Procedural Material 3 Expressive vinyl artwork drawer shared by both the home screen widget
 * and Compose previews. Draws crisp, resolution-independent, dynamic-color-adapted vinyl discs.
 */
object MaterialVinylDrawer {

    fun drawVinyl(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        style: EmptyVinylStyle,
        palette: MaterialVinylPalette
    ) {
        when (style) {
            EmptyVinylStyle.CLASSIC_TONAL -> drawClassicTonal(canvas, centerX, centerY, radius, palette)
            EmptyVinylStyle.EXPRESSIVE_SCALLOP -> drawExpressiveScallop(canvas, centerX, centerY, radius, palette)
            EmptyVinylStyle.DUO_TONAL -> drawDuoTonal(canvas, centerX, centerY, radius, palette)
            EmptyVinylStyle.MINIMAL_SURFACE -> drawMinimalSurface(canvas, centerX, centerY, radius, palette)
            EmptyVinylStyle.CUSTOM_PHOTO -> drawClassicTonal(canvas, centerX, centerY, radius, palette)
        }
    }

    private fun drawClassicTonal(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        p: MaterialVinylPalette
    ) {
        // Base tonal disc
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.surfaceContainerHighest
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r, basePaint)

        // Subtle concentric micro-grooves
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(p.onSecondaryContainer, 38)
            style = Paint.Style.STROKE
            strokeWidth = r * 0.015f
        }
        for (factor in listOf(0.88f, 0.78f, 0.68f, 0.58f, 0.48f)) {
            canvas.drawCircle(cx, cy, r * factor, groovePaint)
        }

        // Diagonal tonal sheen arcs
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(p.primary, 42)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = r * 0.14f
        }
        val arcRect = RectF(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f)
        canvas.drawArc(arcRect, -135f, 52f, false, sheenPaint)
        canvas.drawArc(arcRect, 45f, 52f, false, sheenPaint)

        // Center Material primary label
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.primary
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.36f, labelPaint)

        // Inner label accent ring
        val innerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(p.onPrimary, 90)
            style = Paint.Style.STROKE
            strokeWidth = r * 0.024f
        }
        canvas.drawCircle(cx, cy, r * 0.25f, innerRing)

        // Center spindle cutout
        val spindlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.surface
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.085f, spindlePaint)
    }

    private fun drawExpressiveScallop(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        p: MaterialVinylPalette
    ) {
        // Primary container tonal disc
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.primaryContainer
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r, basePaint)

        // Crisp tonal groove tracks
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(p.onPrimaryContainer, 36)
            style = Paint.Style.STROKE
            strokeWidth = r * 0.018f
        }
        for (factor in listOf(0.86f, 0.73f, 0.60f)) {
            canvas.drawCircle(cx, cy, r * factor, groovePaint)
        }

        // Material 3 Expressive 10-lobe scalloped/cookie center label
        val scallopPath = buildScallopPath(cx, cy, outerRadius = r * 0.44f, innerRadius = r * 0.36f, lobes = 10)
        val scallopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.tertiaryContainer
            style = Paint.Style.FILL
        }
        canvas.drawPath(scallopPath, scallopPaint)

        // Center inner circle
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.primary
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.20f, corePaint)

        // Spindle hole
        val spindlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.primaryContainer
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.075f, spindlePaint)
    }

    private fun drawDuoTonal(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        p: MaterialVinylPalette
    ) {
        // Outer secondary container disc
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.secondaryContainer
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r, outerPaint)

        // Bold tonal color-block rings (Material 3 Expressive high-contrast tonal layering)
        val midBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.primaryContainer
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.78f, midBandPaint)

        val innerBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.tertiaryContainer
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.56f, innerBandPaint)

        // Rhythmic broken groove arcs
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(p.onPrimaryContainer, 70)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = r * 0.03f
        }
        val r1 = RectF(cx - r * 0.88f, cy - r * 0.88f, cx + r * 0.88f, cy + r * 0.88f)
        canvas.drawArc(r1, -50f, 110f, false, arcPaint)
        canvas.drawArc(r1, 130f, 110f, false, arcPaint)

        val r2 = RectF(cx - r * 0.67f, cy - r * 0.67f, cx + r * 0.67f, cy + r * 0.67f)
        canvas.drawArc(r2, 20f, 95f, false, arcPaint)
        canvas.drawArc(r2, 200f, 95f, false, arcPaint)

        // Solid primary center label + spindle
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.primary
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.32f, labelPaint)

        val spindlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.onPrimary
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.085f, spindlePaint)
    }

    private fun drawMinimalSurface(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        p: MaterialVinylPalette
    ) {
        // Ultra-clean tonal surface disc
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.surfaceContainerHigh
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r, basePaint)

        // Two architectural M3 accent groove arcs
        val primaryArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.primary
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = r * 0.055f
        }
        val outerArcRect = RectF(cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f)
        canvas.drawArc(outerArcRect, -110f, 125f, false, primaryArc)

        val tertiaryArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.tertiary
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = r * 0.045f
        }
        val innerArcRect = RectF(cx - r * 0.58f, cy - r * 0.58f, cx + r * 0.58f, cy + r * 0.58f)
        canvas.drawArc(innerArcRect, 70f, 115f, false, tertiaryArc)

        // Subtle single hairline groove
        val hairline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(p.outlineVariant, 140)
            style = Paint.Style.STROKE
            strokeWidth = r * 0.015f
        }
        canvas.drawCircle(cx, cy, r * 0.68f, hairline)

        // Soft center hub
        val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.primaryContainer
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.30f, hubPaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.onPrimaryContainer
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.085f, dotPaint)
    }

    private fun buildScallopPath(
        cx: Float,
        cy: Float,
        outerRadius: Float,
        innerRadius: Float,
        lobes: Int
    ): Path {
        val path = Path()
        val steps = lobes * 12
        for (i in 0..steps) {
            val angle = (2.0 * PI * i) / steps
            val wave = (cos(lobes * angle) + 1.0) / 2.0
            val rad = innerRadius + (outerRadius - innerRadius) * wave
            val x = cx + (rad * cos(angle)).toFloat()
            val y = cy + (rad * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
}
