package com.example.ui.screens

import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ActionButtonStyle
import com.example.data.EmptyVinylStyle
import com.example.media.NotificationAccessHelper
import com.example.ui.VinylOrbitUiState
import com.example.ui.components.CircleWidgetPreview
import com.example.ui.components.CustomizeWidgetSheet
import com.example.ui.components.RemovableAllowedAppRow
import com.example.ui.components.SelectableAppRow
import com.example.ui.components.VinylEmptyStatePicker
import com.example.ui.components.expressiveGroupItemShape
import com.example.widget.CircleMusicWidgetProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: VinylOrbitUiState,
    onToggleApp: (String) -> Unit,
    onOpenAllApps: () -> Unit,
    onPlayStopPreviewClick: () -> Unit,
    onServicePreviewClick: () -> Unit,
    onButtonStyleSelected: (ActionButtonStyle) -> Unit,
    onEmptyVinylStyleSelected: (EmptyVinylStyle) -> Unit,
    onCustomEmptyVinylPicked: (Uri, (Boolean) -> Unit) -> Unit,
    onClearArtWhenStoppedChanged: (Boolean) -> Unit,
    loadAppIcon: suspend (String) -> Drawable?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showManualPinDialog by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker for custom idle vinyl cover art file
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onCustomEmptyVinylPicked(uri) { saved ->
                val messageRes = if (saved) {
                    R.string.custom_vinyl_saved_toast
                } else {
                    R.string.custom_vinyl_error_toast
                }
                Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val onPickCustomVinylImage: () -> Unit = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val onPinWidgetClick: () -> Unit = {
        val requested = CircleMusicWidgetProvider.requestPinWidget(context)
        if (requested) {
            Toast.makeText(
                context,
                context.getString(R.string.widget_pin_requested_toast),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            showManualPinDialog = true
        }
    }

    val onGrantAccessClick: () -> Unit = {
        runCatching {
            context.startActivity(NotificationAccessHelper.createNotificationAccessSettingsIntent())
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    if (uiState.hasNotificationAccess) {
                        AssistChip(
                            onClick = onGrantAccessClick,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.permission_active_badge),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            border = null,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .testTag("access_active_pill")
                        )
                    }

                    FilledTonalIconButton(
                        onClick = { showCustomizeSheet = true },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("customize_widget_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.action_customize_widget)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPinWidgetClick,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.AddToHomeScreen,
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.widget_pin_button)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("pin_widget_button")
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isExpanded = maxWidth >= 820.dp

            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!uiState.hasNotificationAccess) {
                            item {
                                PermissionAccessCard(onGrantClick = onGrantAccessClick)
                            }
                        }
                        item {
                            WidgetHeroStageCard(
                                uiState = uiState,
                                onPlayStopClick = onPlayStopPreviewClick,
                                onServiceClick = onServicePreviewClick,
                                onEmptyVinylStyleSelected = onEmptyVinylStyleSelected,
                                onPickCustomVinylImage = onPickCustomVinylImage,
                                loadAppIcon = loadAppIcon
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(0.54f)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.section_top_apps_title),
                                subtitle = stringResource(R.string.section_top_apps_subtitle),
                                activeCount = uiState.allowedCount
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        itemsIndexed(
                            items = uiState.pinnedApps,
                            key = { _, app -> app.packageName }
                        ) { index, app ->
                            SelectableAppRow(
                                item = app,
                                onToggle = { onToggleApp(app.packageName) },
                                loadAppIcon = loadAppIcon,
                                showPackageSubtitle = false,
                                shape = expressiveGroupItemShape(index, uiState.pinnedApps.size),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        if (uiState.otherEnabledApps.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = stringResource(R.string.section_other_enabled_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                )
                            }
                            itemsIndexed(
                                items = uiState.otherEnabledApps,
                                key = { _, app -> "extra_${app.packageName}" }
                            ) { index, app ->
                                RemovableAllowedAppRow(
                                    item = app,
                                    onRemove = { onToggleApp(app.packageName) },
                                    loadAppIcon = loadAppIcon,
                                    shape = expressiveGroupItemShape(index, uiState.otherEnabledApps.size),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            ShowAllAppsCard(
                                installedCount = uiState.totalInstalledCount,
                                onClick = onOpenAllApps
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 620.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 104.dp
                        )
                    ) {
                        item(key = "permission_banner") {
                            AnimatedVisibility(visible = !uiState.hasNotificationAccess) {
                                Column {
                                    PermissionAccessCard(onGrantClick = onGrantAccessClick)
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }

                        item(key = "widget_stage") {
                            WidgetHeroStageCard(
                                uiState = uiState,
                                onPlayStopClick = onPlayStopPreviewClick,
                                onServiceClick = onServicePreviewClick,
                                onEmptyVinylStyleSelected = onEmptyVinylStyleSelected,
                                onPickCustomVinylImage = onPickCustomVinylImage,
                                loadAppIcon = loadAppIcon
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        item(key = "top_apps_header") {
                            SectionHeader(
                                title = stringResource(R.string.section_top_apps_title),
                                subtitle = stringResource(R.string.section_top_apps_subtitle),
                                activeCount = uiState.allowedCount
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        itemsIndexed(
                            items = uiState.pinnedApps,
                            key = { _, app -> app.packageName }
                        ) { index, app ->
                            SelectableAppRow(
                                item = app,
                                onToggle = { onToggleApp(app.packageName) },
                                loadAppIcon = loadAppIcon,
                                showPackageSubtitle = false,
                                shape = expressiveGroupItemShape(index, uiState.pinnedApps.size),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        if (uiState.otherEnabledApps.isNotEmpty()) {
                            item(key = "other_enabled_header") {
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = stringResource(R.string.section_other_enabled_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                )
                            }

                            itemsIndexed(
                                items = uiState.otherEnabledApps,
                                key = { _, app -> "extra_${app.packageName}" }
                            ) { index, app ->
                                RemovableAllowedAppRow(
                                    item = app,
                                    onRemove = { onToggleApp(app.packageName) },
                                    loadAppIcon = loadAppIcon,
                                    shape = expressiveGroupItemShape(index, uiState.otherEnabledApps.size),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }

                        item(key = "show_all_apps") {
                            Spacer(modifier = Modifier.height(16.dp))
                            ShowAllAppsCard(
                                installedCount = uiState.totalInstalledCount,
                                onClick = onOpenAllApps
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCustomizeSheet) {
        CustomizeWidgetSheet(
            settings = uiState.settings,
            onButtonStyleSelected = onButtonStyleSelected,
            onClearArtWhenStoppedChanged = onClearArtWhenStoppedChanged,
            onDismiss = { showCustomizeSheet = false }
        )
    }

    if (showManualPinDialog) {
        AlertDialog(
            onDismissRequest = { showManualPinDialog = false },
            title = { Text(stringResource(R.string.widget_pin_unsupported_title)) },
            text = { Text(stringResource(R.string.widget_pin_unsupported_body)) },
            confirmButton = {
                TextButton(onClick = { showManualPinDialog = false }) {
                    Text(stringResource(R.string.action_got_it))
                }
            }
        )
    }
}

@Composable
private fun PermissionAccessCard(
    onGrantClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_access_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.permission_needed_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.permission_needed_body),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            FilledTonalButton(
                onClick = onGrantClick,
                modifier = Modifier.testTag("grant_media_access_button")
            ) {
                Text(stringResource(R.string.permission_grant_button))
            }
        }
    }
}

@Composable
private fun WidgetHeroStageCard(
    uiState: VinylOrbitUiState,
    onPlayStopClick: () -> Unit,
    onServiceClick: () -> Unit,
    onEmptyVinylStyleSelected: (EmptyVinylStyle) -> Unit,
    onPickCustomVinylImage: () -> Unit,
    loadAppIcon: suspend (String) -> Drawable?
) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Text(
                    text = stringResource(R.string.widget_stage_label),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            CircleWidgetPreview(
                activeMedia = uiState.activeMedia,
                emptyVinylStyle = uiState.settings.emptyVinylStyle,
                customEmptyVinylBitmap = uiState.customEmptyVinylBitmap,
                buttonStyle = uiState.settings.buttonStyle,
                onPlayStopClick = onPlayStopClick,
                onServiceClick = onServiceClick,
                loadAppIcon = loadAppIcon,
                discDiameter = 208.dp
            )

            val trackTitle = uiState.activeMedia.trackTitle
                ?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.widget_idle_title)
            val subtitleText = if (uiState.activeMedia.hasActiveTrack) {
                listOfNotNull(
                    uiState.activeMedia.artistName?.takeIf { it.isNotBlank() },
                    uiState.activeMedia.appName?.takeIf { it.isNotBlank() }
                ).joinToString(" • ").ifEmpty { stringResource(R.string.widget_idle_subtitle) }
            } else {
                stringResource(R.string.widget_idle_subtitle)
            }

            Text(
                text = trackTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Idle vinyl empty-state preset files & custom photo picker strip
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.section_vinyl_empty_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                VinylEmptyStatePicker(
                    selectedStyle = uiState.settings.emptyVinylStyle,
                    customBitmap = uiState.customEmptyVinylBitmap,
                    onStyleSelected = onEmptyVinylStyleSelected,
                    onPickCustomImageClick = onPickCustomVinylImage
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    activeCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Text(
                text = stringResource(R.string.active_count_format, activeCount),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ShowAllAppsCard(
    installedCount: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag("show_all_apps_button")
    ) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                headlineColor = MaterialTheme.colorScheme.onSecondaryContainer,
                supportingColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                leadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                trailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            leadingContent = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            },
            headlineContent = {
                Text(
                    text = stringResource(R.string.action_show_all_apps),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = if (installedCount > 0) {
                        stringResource(R.string.installed_count_format, installedCount)
                    } else {
                        stringResource(R.string.show_all_apps_subtitle)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null
                )
            }
        )
    }
}
