package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.data.ActionButtonStyle
import com.example.data.CuratedMusicApps
import com.example.data.CustomVinylImageStore
import com.example.data.EmptyVinylStyle

/**
 * Renders anti-aliased, borderless Material You bitmaps for the RemoteViews home screen widget:
 *  - Center circular album art (or Material-adapted procedural vinyl disc when idle)
 *  - Bottom-left tactile Material 3 Stop / Pause / Play button
 *  - Top-right Material You monochrome themed source service icon badge
 */
object WidgetBitmapRenderer {

    fun renderCircularAlbumArt(
        context: Context,
        albumArt: Bitmap?,
        emptyVinylStyle: EmptyVinylStyle,
        sizePx: Int = 384
    ): Bitmap {
        val safeSize = sizePx.coerceIn(192, 720)
        val output = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val center = safeSize / 2f
        val radius = center - safeSize * 0.006f

        val activeBitmap = albumArt?.takeIf { !it.isRecycled }
        val customPhoto = if (activeBitmap == null && emptyVinylStyle == EmptyVinylStyle.CUSTOM_PHOTO) {
            CustomVinylImageStore.loadBitmap(context)?.takeIf { !it.isRecycled }
        } else {
            null
        }
        val bitmapToCrop = activeBitmap ?: customPhoto

        if (bitmapToCrop != null) {
            val shader = BitmapShader(bitmapToCrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val matrix = Matrix()
            val diameter = radius * 2f
            val scale = maxOf(diameter / bitmapToCrop.width, diameter / bitmapToCrop.height)
            val dx = center - (bitmapToCrop.width * scale) / 2f
            val dy = center - (bitmapToCrop.height * scale) / 2f
            matrix.setScale(scale, scale)
            matrix.postTranslate(dx, dy)
            shader.setLocalMatrix(matrix)

            val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.shader = shader
            }
            canvas.drawCircle(center, center, radius, artPaint)
        } else {
            val palette = MaterialVinylPalette.fromContext(context)
            MaterialVinylDrawer.drawVinyl(
                canvas = canvas,
                centerX = center,
                centerY = center,
                radius = radius,
                style = emptyVinylStyle,
                palette = palette
            )
        }

        return output
    }

    fun renderPlayStopButton(
        context: Context,
        isPlaying: Boolean,
        buttonStyle: ActionButtonStyle,
        sizePx: Int = 144
    ): Bitmap {
        val safeSize = sizePx.coerceIn(96, 256)
        val output = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val center = safeSize / 2f
        val palette = MaterialVinylPalette.fromContext(context)
        val radius = center - safeSize * 0.04f

        val fillColor = if (isPlaying) palette.primary else palette.primaryContainer
        val glyphColor = if (isPlaying) palette.onPrimary else palette.onPrimaryContainer

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glyphColor
            style = Paint.Style.FILL
        }

        if (isPlaying) {
            if (buttonStyle == ActionButtonStyle.STOP_PLAY) {
                val half = safeSize * 0.175f
                val corner = safeSize * 0.06f
                val rect = RectF(center - half, center - half, center + half, center + half)
                canvas.drawRoundRect(rect, corner, corner, glyphPaint)
            } else {
                val barW = safeSize * 0.105f
                val barH = safeSize * 0.35f
                val gap = safeSize * 0.085f
                val corner = safeSize * 0.04f
                val leftBar = RectF(
                    center - gap / 2f - barW,
                    center - barH / 2f,
                    center - gap / 2f,
                    center + barH / 2f
                )
                val rightBar = RectF(
                    center + gap / 2f,
                    center - barH / 2f,
                    center + gap / 2f + barW,
                    center + barH / 2f
                )
                canvas.drawRoundRect(leftBar, corner, corner, glyphPaint)
                canvas.drawRoundRect(rightBar, corner, corner, glyphPaint)
            }
        } else {
            val triHeight = safeSize * 0.38f
            val triWidth = safeSize * 0.32f
            val offsetX = safeSize * 0.04f
            val path = Path().apply {
                moveTo(center - triWidth / 2f + offsetX, center - triHeight / 2f)
                lineTo(center + triWidth / 2f + offsetX, center)
                lineTo(center - triWidth / 2f + offsetX, center + triHeight / 2f)
                close()
            }
            canvas.drawPath(path, glyphPaint)
        }

        return output
    }

    /**
     * Renders a Material You monochrome themed circular service badge:
     *  - Background circle: palette.secondaryContainer
     *  - Foreground glyph: palette.onSecondaryContainer (using curated monochrome glyph
     *    for YT Music / Spotify / TIDAL / Idle, or the app's Android 13+ monochrome adaptive
     *    icon layer if available, falling back to a cleanly tinted silhouette).
     */
    fun renderServiceBadge(
        context: Context,
        packageName: String?,
        sizePx: Int = 128
    ): Bitmap {
        val safeSize = sizePx.coerceIn(84, 224)
        val output = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val center = safeSize / 2f
        val radius = center - safeSize * 0.04f
        val palette = MaterialVinylPalette.fromContext(context)

        // Material 3 tonal circular badge background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.secondaryContainer
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val useCuratedGlyph = packageName == null ||
            packageName == CuratedMusicApps.PKG_YT_MUSIC ||
            packageName == CuratedMusicApps.PKG_SPOTIFY ||
            packageName == CuratedMusicApps.PKG_TIDAL

        if (!useCuratedGlyph && packageName != null) {
            val appIcon = runCatching {
                context.packageManager.getApplicationIcon(packageName)
            }.getOrNull()
            if (appIcon != null) {
                if (drawMonochromeAdaptiveIcon(canvas, appIcon, safeSize, palette.onSecondaryContainer)) {
                    return output
                }
                drawTintedIconFallback(canvas, appIcon, safeSize, palette.onSecondaryContainer)
                return output
            }
        }

        // Draw curated monochrome Material You glyph tinted in onSecondaryContainer
        val glyphRes = CuratedMusicApps.monochromeGlyphFor(packageName)
        val drawable = ContextCompat.getDrawable(context, glyphRes)?.mutate()
        if (drawable != null) {
            DrawableCompat.setTint(drawable, palette.onSecondaryContainer)
            val pad = (safeSize * 0.22f).toInt()
            drawable.setBounds(pad, pad, safeSize - pad, safeSize - pad)
            drawable.draw(canvas)
        }

        return output
    }

    private fun drawMonochromeAdaptiveIcon(
        canvas: Canvas,
        appIcon: Drawable,
        sizePx: Int,
        tintColor: Int
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && appIcon is AdaptiveIconDrawable) {
            val mono = appIcon.monochrome?.constantState?.newDrawable()?.mutate() ?: return false
            mono.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            // Adaptive icon layers include 18% inset margin per side, so expand slightly to fill cleanly
            val inset = (sizePx * 0.06f).toInt()
            mono.setBounds(inset, inset, sizePx - inset, sizePx - inset)
            mono.draw(canvas)
            return true
        }
        return false
    }

    private fun drawTintedIconFallback(
        canvas: Canvas,
        appIcon: Drawable,
        sizePx: Int,
        tintColor: Int
    ) {
        val pad = (sizePx * 0.22f).toInt()
        val innerSize = (sizePx - pad * 2).coerceAtLeast(1)
        val temp = Bitmap.createBitmap(innerSize, innerSize, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(temp)
        val foreground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appIcon is AdaptiveIconDrawable) {
            appIcon.foreground ?: appIcon
        } else {
            appIcon
        }
        foreground.setBounds(0, 0, innerSize, innerSize)
        foreground.draw(tempCanvas)

        val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(temp, pad.toFloat(), pad.toFloat(), tintPaint)
    }
}
