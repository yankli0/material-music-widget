package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.EmptyVinylStyle

@Composable
fun VinylEmptyStatePicker(
    selectedStyle: EmptyVinylStyle,
    customBitmap: Bitmap?,
    onStyleSelected: (EmptyVinylStyle) -> Unit,
    onPickCustomImageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val materialStyles = remember {
        listOf(
            EmptyVinylStyle.CLASSIC_TONAL to R.string.vinyl_style_classic_tonal,
            EmptyVinylStyle.EXPRESSIVE_SCALLOP to R.string.vinyl_style_expressive_scallop,
            EmptyVinylStyle.DUO_TONAL to R.string.vinyl_style_duo_tonal,
            EmptyVinylStyle.MINIMAL_SURFACE to R.string.vinyl_style_minimal_surface
        )
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(materialStyles, key = { it.first.key }) { (style, labelRes) ->
            val isSelected = selectedStyle == style
            VinylStyleThumbnail(
                label = stringResource(labelRes),
                isSelected = isSelected,
                onClick = { onStyleSelected(style) },
                modifier = Modifier.testTag("vinyl_preset_${style.key}")
            ) {
                MaterialVinylDiscCanvas(
                    style = style,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Optional custom image file via Android Photo Picker
        item(key = EmptyVinylStyle.CUSTOM_PHOTO.key) {
            val isSelected = selectedStyle == EmptyVinylStyle.CUSTOM_PHOTO && customBitmap != null
            VinylStyleThumbnail(
                label = if (customBitmap != null) {
                    stringResource(R.string.vinyl_preset_custom)
                } else {
                    stringResource(R.string.action_pick_custom_vinyl)
                },
                isSelected = isSelected,
                onClick = onPickCustomImageClick,
                modifier = Modifier.testTag("vinyl_preset_custom_photo")
            ) {
                if (customBitmap != null && !customBitmap.isRecycled) {
                    Image(
                        bitmap = remember(customBitmap) { customBitmap.asImageBitmap() },
                        contentDescription = stringResource(R.string.vinyl_preset_custom),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddPhotoAlternate,
                            contentDescription = stringResource(R.string.action_pick_custom_vinyl),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VinylStyleThumbnail(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.07f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "vinyl_thumb_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .width(68.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .scale(scale),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                tonalElevation = if (isSelected) 6.dp else 1.dp,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
            ) {
                content()
            }

            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
