package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ActionButtonStyle
import com.example.data.ActiveMediaState
import com.example.data.AppListFilter
import com.example.data.CustomVinylImageStore
import com.example.data.DiscoveredDeviceApp
import com.example.data.EmptyVinylStyle
import com.example.data.InstalledAppsRepository
import com.example.data.PlaybackSnapshotStore
import com.example.data.SelectableAppItem
import com.example.data.WidgetPreferencesRepository
import com.example.data.WidgetSettings
import com.example.media.MediaPlaybackController
import com.example.media.MediaSessionListenerService
import com.example.media.MediaSessionStateHolder
import com.example.media.NotificationAccessHelper
import com.example.widget.CircleMusicWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VinylOrbitUiState(
    val isLoadingApps: Boolean = true,
    val hasNotificationAccess: Boolean = false,
    val settings: WidgetSettings = WidgetSettings(),
    val activeMedia: ActiveMediaState = ActiveMediaState(),
    val customEmptyVinylBitmap: Bitmap? = null,
    val pinnedApps: List<SelectableAppItem> = emptyList(),
    val otherEnabledApps: List<SelectableAppItem> = emptyList(),
    val filteredAllApps: List<SelectableAppItem> = emptyList(),
    val totalInstalledCount: Int = 0,
    val allowedCount: Int = 0,
    val searchQuery: String = "",
    val selectedFilter: AppListFilter = AppListFilter.ALL
)

class VinylOrbitViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferencesRepository = WidgetPreferencesRepository(appContext)
    private val installedAppsRepository = InstalledAppsRepository(appContext)

    private val discoveredApps = MutableStateFlow<List<DiscoveredDeviceApp>>(emptyList())
    private val isLoadingApps = MutableStateFlow(true)
    private val hasNotificationAccess = MutableStateFlow(
        NotificationAccessHelper.isNotificationListenerEnabled(appContext)
    )
    private val customVinylBitmap = MutableStateFlow<Bitmap?>(null)
    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(AppListFilter.ALL)

    private val filterInputs = combine(
        searchQuery,
        selectedFilter,
        hasNotificationAccess,
        isLoadingApps,
        customVinylBitmap
    ) { query, filter, access, loading, customBmp ->
        FilterAndStatus(query, filter, access, loading, customBmp)
    }

    val uiState: StateFlow<VinylOrbitUiState> = combine(
        preferencesRepository.settingsFlow,
        MediaSessionStateHolder.activeMediaState,
        discoveredApps,
        filterInputs
    ) { settings, rawMedia, apps, meta ->
        val selectableApps = apps.map { app ->
            SelectableAppItem(
                packageName = app.packageName,
                displayName = app.displayName,
                isInstalled = app.isInstalled,
                isPinnedTop = app.isPinnedTop,
                isMusicApp = app.isMusicApp,
                isAllowed = app.packageName in settings.allowedPackages,
                brandAccentArgb = app.brandAccentArgb
            )
        }

        val pinned = selectableApps.filter { it.isPinnedTop }
        val otherEnabled = selectableApps.filter { !it.isPinnedTop && it.isAllowed }

        val trimmedQuery = meta.searchQuery.trim().lowercase()
        val filteredAll = selectableApps.filter { item ->
            val matchesFilter = when (meta.selectedFilter) {
                AppListFilter.ALL -> true
                AppListFilter.MUSIC -> item.isMusicApp
                AppListFilter.ENABLED -> item.isAllowed
            }
            val matchesSearch = trimmedQuery.isEmpty() ||
                item.displayName.lowercase().contains(trimmedQuery) ||
                item.packageName.lowercase().contains(trimmedQuery)
            matchesFilter && matchesSearch
        }

        val filteredMedia = if (
            rawMedia.packageName != null &&
            rawMedia.packageName in settings.allowedPackages &&
            !(settings.clearArtWhenStopped && !rawMedia.isPlaying)
        ) {
            rawMedia
        } else {
            ActiveMediaState()
        }

        VinylOrbitUiState(
            isLoadingApps = meta.isLoadingApps,
            hasNotificationAccess = meta.hasNotificationAccess,
            settings = settings,
            activeMedia = filteredMedia,
            customEmptyVinylBitmap = meta.customVinylBitmap,
            pinnedApps = pinned,
            otherEnabledApps = otherEnabled,
            filteredAllApps = filteredAll,
            totalInstalledCount = apps.count { it.isInstalled },
            allowedCount = settings.allowedPackages.size,
            searchQuery = meta.searchQuery,
            selectedFilter = meta.selectedFilter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VinylOrbitUiState()
    )

    init {
        MediaSessionStateHolder.hydrateFromSnapshotIfEmpty(appContext)
        loadSavedCustomVinyl()
        refreshDeviceState()
    }

    private fun loadSavedCustomVinyl() {
        viewModelScope.launch {
            customVinylBitmap.value = withContext(Dispatchers.IO) {
                CustomVinylImageStore.loadBitmap(appContext)
            }
        }
    }

    fun refreshDeviceState() {
        val enabled = NotificationAccessHelper.isNotificationListenerEnabled(appContext)
        hasNotificationAccess.value = enabled
        if (enabled) {
            MediaPlaybackController.requestRebindIfEnabled(appContext)
            MediaSessionListenerService.triggerRecompute(appContext)
        }
        viewModelScope.launch {
            isLoadingApps.value = true
            discoveredApps.value = installedAppsRepository.loadDeviceApps()
            isLoadingApps.value = false
        }
        CircleMusicWidgetProvider.updateAllWidgets(appContext)
    }

    fun toggleAppAllowed(packageName: String) {
        viewModelScope.launch {
            preferencesRepository.togglePackage(packageName)
            val updated = preferencesRepository.getCurrentSettings()
            val currentMedia = MediaSessionStateHolder.activeMediaState.value
            if (currentMedia.packageName != null && currentMedia.packageName !in updated.allowedPackages) {
                MediaSessionStateHolder.update(ActiveMediaState())
                PlaybackSnapshotStore.clear(appContext)
            }
            MediaSessionListenerService.triggerRecompute(appContext)
            CircleMusicWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun resetToDefaultServices() {
        viewModelScope.launch {
            preferencesRepository.resetToDefaultServices()
            MediaSessionListenerService.triggerRecompute(appContext)
            CircleMusicWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun setButtonStyle(style: ActionButtonStyle) {
        viewModelScope.launch {
            preferencesRepository.setButtonStyle(style)
            CircleMusicWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun setEmptyVinylStyle(style: EmptyVinylStyle) {
        viewModelScope.launch {
            preferencesRepository.setEmptyVinylStyle(style)
            CircleMusicWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun saveCustomEmptyVinylImage(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val saved = CustomVinylImageStore.saveFromUri(appContext, uri)
            if (saved) {
                customVinylBitmap.value = withContext(Dispatchers.IO) {
                    CustomVinylImageStore.loadBitmap(appContext)
                }
                preferencesRepository.markCustomVinylUpdated()
                CircleMusicWidgetProvider.updateAllWidgets(appContext)
            }
            onComplete(saved)
        }
    }

    fun setClearArtWhenStopped(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setClearArtWhenStopped(enabled)
            MediaSessionListenerService.triggerRecompute(appContext)
            CircleMusicWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateFilter(filter: AppListFilter) {
        selectedFilter.value = filter
    }

    fun onWidgetPreviewPlayStopClicked() {
        viewModelScope.launch {
            MediaPlaybackController.togglePlayStop(appContext)
            CircleMusicWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun onWidgetPreviewServiceClicked() {
        val pkg = uiState.value.activeMedia.packageName
        if (pkg != null) {
            MediaPlaybackController.openSourceApp(appContext, pkg)
        }
    }

    suspend fun loadAppIcon(packageName: String): Drawable? =
        installedAppsRepository.loadAppIcon(packageName)

    private data class FilterAndStatus(
        val searchQuery: String,
        val selectedFilter: AppListFilter,
        val hasNotificationAccess: Boolean,
        val isLoadingApps: Boolean,
        val customVinylBitmap: Bitmap?
    )
}
