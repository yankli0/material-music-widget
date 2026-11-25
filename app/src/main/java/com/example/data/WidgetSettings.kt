package com.example.data

import android.graphics.Bitmap

enum class ActionButtonStyle(val key: String) {
    STOP_PLAY("stop_play"),
    PAUSE_PLAY("pause_play");

    companion object {
        fun fromKey(key: String?): ActionButtonStyle =
            entries.firstOrNull { it.key == key } ?: STOP_PLAY
    }
}

/**
 * Material You / Material 3 Expressive dynamic-color-adapted vinyl empty-state designs,
 * plus an optional user-picked custom photo file.
 */
enum class EmptyVinylStyle(val key: String) {
    CLASSIC_TONAL("classic_tonal"),
    EXPRESSIVE_SCALLOP("expressive_scallop"),
    DUO_TONAL("duo_tonal"),
    MINIMAL_SURFACE("minimal_surface"),
    CUSTOM_PHOTO("custom_photo");

    companion object {
        fun fromKey(key: String?): EmptyVinylStyle =
            entries.firstOrNull { it.key == key } ?: CLASSIC_TONAL
    }
}

data class WidgetSettings(
    val allowedPackages: Set<String> = CuratedMusicApps.defaultAllowedPackages,
    val buttonStyle: ActionButtonStyle = ActionButtonStyle.STOP_PLAY,
    val emptyVinylStyle: EmptyVinylStyle = EmptyVinylStyle.CLASSIC_TONAL,
    val customVinylVersion: Long = 0L,
    val clearArtWhenStopped: Boolean = false
)

data class ActiveMediaState(
    val packageName: String? = null,
    val appName: String? = null,
    val trackTitle: String? = null,
    val artistName: String? = null,
    val isPlaying: Boolean = false,
    val albumArt: Bitmap? = null,
    val updatedAtMillis: Long = 0L
) {
    val hasActiveTrack: Boolean
        get() = packageName != null
}

data class SelectableAppItem(
    val packageName: String,
    val displayName: String,
    val isInstalled: Boolean,
    val isPinnedTop: Boolean,
    val isMusicApp: Boolean,
    val isAllowed: Boolean,
    val brandAccentArgb: Long? = null
)

enum class AppListFilter {
    ALL,
    MUSIC,
    ENABLED
}
