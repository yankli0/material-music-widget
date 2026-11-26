package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.data.CuratedMusicApps
import com.example.data.SelectableAppItem

/**
 * Material 3 Expressive segmented group shape: outer ends get generous 24.dp radii,
 * while inner adjoining edges use crisp 6.dp radii with 2.dp gaps between rows.
 */
fun expressiveGroupItemShape(index: Int, count: Int): Shape {
    val outer = 24.dp
    val inner = 6.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(
            topStart = outer,
            topEnd = outer,
            bottomStart = inner,
            bottomEnd = inner
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = inner,
            topEnd = inner,
            bottomStart = outer,
            bottomEnd = outer
        )
        else -> RoundedCornerShape(inner)
    }
}

@Composable
fun SelectableAppRow(
    item: SelectableAppItem,
    onToggle: () -> Unit,
    loadAppIcon: suspend (String) -> Drawable?,
    showPackageSubtitle: Boolean,
    shape: Shape = RoundedCornerShape(24.dp),
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onToggle)
            .testTag("app_row_${item.packageName}")
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = containerColor),
            leadingContent = {
                AppIconAvatar(
                    item = item,
                    loadAppIcon = loadAppIcon
                )
            },
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isPinnedTop) {
                        TonalMetaBadge(
                            text = stringResource(R.string.badge_pinned),
                            isPrimary = true
                        )
                    } else if (item.isMusicApp && showPackageSubtitle) {
                        TonalMetaBadge(
                            text = stringResource(R.string.badge_media),
                            isPrimary = false
                        )
                    }
                }
            },
            supportingContent = {
                if (showPackageSubtitle) {
                    Text(
                        text = item.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = if (item.isInstalled) {
                            stringResource(R.string.status_installed)
                        } else {
                            stringResource(R.string.status_not_installed)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isInstalled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            },
            trailingContent = {
                Switch(
                    checked = item.isAllowed,
                    onCheckedChange = { onToggle() },
                    thumbContent = if (item.isAllowed) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag("app_switch_${item.packageName}")
                )
            }
        )
    }
}

/**
 * Used in the "Also Allowed" section on HomeScreen where removing an app removes the row
 * from the section — uses a clear cross remove button instead of a toggle switch.
 */
@Composable
fun RemovableAllowedAppRow(
    item: SelectableAppItem,
    onRemove: () -> Unit,
    loadAppIcon: suspend (String) -> Drawable?,
    shape: Shape = RoundedCornerShape(24.dp),
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .testTag("allowed_app_row_${item.packageName}")
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = containerColor),
            leadingContent = {
                AppIconAvatar(
                    item = item,
                    loadAppIcon = loadAppIcon
                )
            },
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isMusicApp) {
                        TonalMetaBadge(
                            text = stringResource(R.string.badge_media),
                            isPrimary = false
                        )
                    }
                }
            },
            supportingContent = {
                Text(
                    text = item.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = {
                FilledTonalIconButton(
                    onClick = onRemove,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("remove_app_button_${item.packageName}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_remove_app)
                    )
                }
            }
        )
    }
}

@Composable
private fun AppIconAvatar(
    item: SelectableAppItem,
    loadAppIcon: suspend (String) -> Drawable?
) {
    val useCuratedGlyph = item.packageName == CuratedMusicApps.PKG_YT_MUSIC ||
        item.packageName == CuratedMusicApps.PKG_SPOTIFY ||
        item.packageName == CuratedMusicApps.PKG_TIDAL

    var iconBitmap by remember(item.packageName) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.packageName, useCuratedGlyph, item.isInstalled) {
        if (!useCuratedGlyph && item.isInstalled) {
            val drawable = loadAppIcon(item.packageName)
            iconBitmap = drawable?.let {
                runCatching { it.toBitmap(96, 96, Bitmap.Config.ARGB_8888) }.getOrNull()
            }
        } else {
            iconBitmap = null
        }
    }

    val avatarBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val glyphTint = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(avatarBg),
        contentAlignment = Alignment.Center
    ) {
        val loaded = iconBitmap
        if (!useCuratedGlyph && loaded != null) {
            Image(
                bitmap = remember(loaded) { loaded.asImageBitmap() },
                contentDescription = stringResource(R.string.cd_app_icon),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
            )
        } else {
            Icon(
                painter = painterResource(id = CuratedMusicApps.monochromeGlyphFor(item.packageName)),
                contentDescription = stringResource(R.string.cd_app_icon),
                tint = glyphTint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun TonalMetaBadge(
    text: String,
    isPrimary: Boolean
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isPrimary) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isPrimary) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
