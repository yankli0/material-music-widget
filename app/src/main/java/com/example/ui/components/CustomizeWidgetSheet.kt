package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ActionButtonStyle
import com.example.data.WidgetSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeWidgetSheet(
    settings: WidgetSettings,
    onButtonStyleSelected: (ActionButtonStyle) -> Unit,
    onClearArtWhenStoppedChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.customize_sheet_title),
                style = MaterialTheme.typography.headlineSmall
            )

            // Material 3 SegmentedButton for bottom-left icon style
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.customize_button_style_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val buttonOptions = listOf(
                    ActionButtonStyle.STOP_PLAY to stringResource(R.string.style_stop_play),
                    ActionButtonStyle.PAUSE_PLAY to stringResource(R.string.style_pause_play)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    buttonOptions.forEachIndexed { index, (style, label) ->
                        SegmentedButton(
                            selected = settings.buttonStyle == style,
                            onClick = { onButtonStyleSelected(style) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = buttonOptions.size
                            ),
                            modifier = Modifier.testTag("button_style_${style.key}")
                        ) {
                            Text(text = label, maxLines = 1)
                        }
                    }
                }
            }

            // Standard Material 3 ListItem toggle
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .testTag("toggle_clear_art_on_stop")
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.customize_clear_on_pause_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.customize_clear_on_pause_subtitle),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.clearArtWhenStopped,
                            onCheckedChange = onClearArtWhenStoppedChanged,
                            thumbContent = if (settings.clearArtWhenStopped) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                )
            }
        }
    }
}
