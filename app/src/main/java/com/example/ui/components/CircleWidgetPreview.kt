package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.data.ActionButtonStyle
import com.example.data.ActiveMediaState
import com.example.data.CuratedMusicApps
import com.example.data.EmptyVinylStyle
import com.example.widget.MaterialVinylDrawer
import com.example.widget.MaterialVinylPalette

fun ColorScheme.toMaterialVinylPalette(): MaterialVinylPalette = MaterialVinylPalette(
    primary = primary.toArgb(),
    onPrimary = onPrimary.toArgb(),
    primaryContainer = primaryContainer.toArgb(),
    onPrimaryContainer = onPrimaryContainer.toArgb(),
    secondary = secondary.toArgb(),
    secondaryContainer = secondaryContainer.toArgb(),
    onSecondaryContainer = onSecondaryContainer.toArgb(),
    tertiary = tertiary.toArgb(),
    tertiaryContainer = tertiaryContainer.toArgb(),
    onTertiaryContainer = onTertiaryContainer.toArgb(),
    surface = surface.toArgb(),
    surfaceContainerLow = surfaceContainerLow.toArgb(),
    surfaceContainerHigh = surfaceContainerHigh.toArgb(),
    surfaceContainerHighest = surfaceContainerHighest.toArgb(),
    outlineVariant = outlineVariant.toArgb()
)

@Composable
fun MaterialVinylDiscCanvas(
    style: EmptyVinylStyle,
    modifier: Modifier = Modifier
) {
    val palette = MaterialTheme.colorScheme.toMaterialVinylPalette()
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        drawIntoCanvas { canvas ->
            MaterialVinylDrawer.drawVinyl(
                canvas = canvas.nativeCanvas,
                centerX = size.width / 2f,
                centerY = size.height / 2f,
                radius = radius,
                style = style,
                palette = palette
            )
        }
    }
}

/**
 * Borderless Material 3 Expressive preview of the Vinyl Orbit circular home screen widget.
 *
 * Geometry:
 *  - Center circle has radius R = discDiameter / 2.
 *  - Bottom-left Stop/Play button sits directly on the circle perimeter at 135 deg:
 *    (-0.636 * R, +0.636 * R) from Alignment.Center.
 *  - Right Service icon badge sits directly on the circle perimeter at 0 deg:
 *    (+0.90 * R, 0) from Alignment.Center.
 */
@Composable
fun CircleWidgetPreview(
    activeMedia: ActiveMediaState,
    emptyVinylStyle: EmptyVinylStyle,
    customEmptyVinylBitmap: Bitmap?,
    buttonStyle: ActionButtonStyle,
    onPlayStopClick: () -> Unit,
    onServiceClick: () -> Unit,
    loadAppIcon: suspend (String) -> Drawable?,
    modifier: Modifier = Modifier,
    discDiameter: Dp = 208.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val spinDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "disc_rotation"
    )
    val effectiveRotation = if (activeMedia.isPlaying) spinDegrees else 0f

    val playButtonScale by animateFloatAsState(
        targetValue = if (activeMedia.isPlaying) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "play_btn_scale"
    )

    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val widgetPalette = remember(context, isDark) { MaterialVinylPalette.fromContext(context) }
    val buttonSize = discDiameter * 0.325f
    val badgeSize = discDiameter * 0.26f

    Box(
        modifier = modifier.size(discDiameter),
        contentAlignment = Alignment.Center
    ) {
        // CENTER: Borderless circular album art or Material-adapted idle vinyl disc
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(discDiameter)
                .clip(CircleShape)
                .clickable(onClick = onServiceClick)
                .testTag("widget_preview_album_art")
        ) {
            val coverBitmap = activeMedia.albumArt?.takeIf { !it.isRecycled }
            val customIdleBitmap = if (emptyVinylStyle == EmptyVinylStyle.CUSTOM_PHOTO) {
                customEmptyVinylBitmap?.takeIf { !it.isRecycled }
            } else {
                null
            }

            when {
                coverBitmap != null -> {
                    val imageBitmap = remember(coverBitmap) { coverBitmap.asImageBitmap() }
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = stringResource(R.string.cd_album_art),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(effectiveRotation)
                    )
                }

                customIdleBitmap != null -> {
                    val imageBitmap = remember(customIdleBitmap) { customIdleBitmap.asImageBitmap() }
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = stringResource(R.string.cd_album_art),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(effectiveRotation)
                    )
                }

                else -> {
                    MaterialVinylDiscCanvas(
                        style = emptyVinylStyle,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(effectiveRotation)
                    )
                }
            }
        }

        // BOTTOM-LEFT: M3 rounded-square Stop / Pause / Play FAB flush to bottom-start tangent
        val buttonContainerColor = if (activeMedia.isPlaying) {
            Color(widgetPalette.primary)
        } else {
            Color(widgetPalette.primaryContainer)
        }
        val buttonContentColor = when {
            activeMedia.isPlaying -> Color(widgetPalette.onPrimary)
            isDark -> Color(widgetPalette.onPrimaryContainer)
            else -> Color(widgetPalette.primary)
        }
        val controlIcon = when {
            !activeMedia.isPlaying -> Icons.Rounded.PlayArrow
            buttonStyle == ActionButtonStyle.STOP_PLAY -> Icons.Rounded.Stop
            else -> Icons.Rounded.Pause
        }
        val controlCd = if (activeMedia.isPlaying) {
            stringResource(R.string.cd_stop_button)
        } else {
            stringResource(R.string.cd_play_button)
        }

        Surface(
            onClick = onPlayStopClick,
            shape = RoundedCornerShape(percent = 33),
            color = buttonContainerColor,
            contentColor = buttonContentColor,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .scale(playButtonScale)
                .size(buttonSize)
                .testTag("widget_preview_play_stop_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = controlIcon,
                    contentDescription = controlCd,
                    modifier = Modifier.size(buttonSize * 0.38f)
                )
            }
        }

        // TOP-RIGHT: Tertiary-tinted circular source service badge flush to top-end tangent
        ServiceSourceBadge(
            packageName = activeMedia.packageName,
            loadAppIcon = loadAppIcon,
            onClick = onServiceClick,
            badgeSize = badgeSize,
            containerColor = Color(widgetPalette.tertiaryContainer),
            glyphColor = if (isDark) Color(widgetPalette.onTertiaryContainer) else Color(widgetPalette.tertiary),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .testTag("widget_preview_service_badge")
        )
    }
}

@Composable
private fun ServiceSourceBadge(
    packageName: String?,
    loadAppIcon: suspend (String) -> Drawable?,
    onClick: () -> Unit,
    badgeSize: Dp,
    containerColor: Color,
    glyphColor: Color,
    modifier: Modifier = Modifier
) {
    val useCuratedGlyph = packageName == null ||
        packageName == CuratedMusicApps.PKG_YT_MUSIC ||
        packageName == CuratedMusicApps.PKG_SPOTIFY ||
        packageName == CuratedMusicApps.PKG_TIDAL

    var loadedBitmap by remember(packageName) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(packageName, useCuratedGlyph) {
        if (!useCuratedGlyph && packageName != null) {
            val drawable = loadAppIcon(packageName)
            loadedBitmap = drawable?.let { d ->
                val monoOrFg = if (
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    d is android.graphics.drawable.AdaptiveIconDrawable
                ) {
                    d.monochrome ?: d.foreground ?: d
                } else if (
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                    d is android.graphics.drawable.AdaptiveIconDrawable
                ) {
                    d.foreground ?: d
                } else {
                    d
                }
                runCatching { monoOrFg.toBitmap(96, 96, Bitmap.Config.ARGB_8888) }.getOrNull()
            }
        } else {
            loadedBitmap = null
        }
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = glyphColor,
        shadowElevation = 4.dp,
        modifier = modifier.size(badgeSize)
    ) {
        Box(contentAlignment = Alignment.Center) {
            val customBmp = loadedBitmap
            if (!useCuratedGlyph && customBmp != null) {
                Icon(
                    bitmap = remember(customBmp) { customBmp.asImageBitmap() },
                    contentDescription = stringResource(R.string.cd_service_badge),
                    tint = glyphColor,
                    modifier = Modifier.size(badgeSize * 0.46f)
                )
            } else {
                Icon(
                    painter = painterResource(id = CuratedMusicApps.monochromeGlyphFor(packageName)),
                    contentDescription = stringResource(R.string.cd_service_badge),
                    tint = glyphColor,
                    modifier = Modifier.size(badgeSize * 0.46f)
                )
            }
        }
    }
}
