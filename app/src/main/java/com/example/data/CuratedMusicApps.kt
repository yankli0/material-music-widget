package com.example.data

import androidx.annotation.DrawableRes
import com.example.R

data class KnownMusicService(
    val packageName: String,
    val displayName: String,
    val isPinnedTop: Boolean,
    val brandAccentArgb: Long,
    @DrawableRes val fallbackIconRes: Int? = null
)

object CuratedMusicApps {
    const val PKG_YT_MUSIC = "com.google.android.apps.youtube.music"
    const val PKG_SPOTIFY = "com.spotify.music"
    const val PKG_TIDAL = "com.aspiro.tidal"

    const val PKG_APPLE_MUSIC = "com.apple.android.music"
    const val PKG_SOUNDCLOUD = "com.soundcloud.android"
    const val PKG_DEEZER = "deezer.android.app"
    const val PKG_AMAZON_MUSIC = "com.amazon.mp3"
    const val PKG_PANDORA = "com.pandora.android"
    const val PKG_QOBUZ = "com.qobuz.music"
    const val PKG_BANDCAMP = "com.bandcamp.android"
    const val PKG_YOUTUBE = "com.google.android.youtube"
    const val PKG_VLC = "org.videolan.vlc"
    const val PKG_POWERAMP = "com.maxmpz.audioplayer"
    const val PKG_AUDIBLE = "com.audible.application"
    const val PKG_POCKET_CASTS = "com.pocketcasts.android"

    /**
     * Featured services pinned at the top of the app picker.
     * Enabled by default on first launch.
     */
    val pinnedTopServices: List<KnownMusicService> = listOf(
        KnownMusicService(
            packageName = PKG_YT_MUSIC,
            displayName = "YouTube Music",
            isPinnedTop = true,
            brandAccentArgb = 0xFFFF0033L,
            fallbackIconRes = R.drawable.ic_service_yt_music
        ),
        KnownMusicService(
            packageName = PKG_SPOTIFY,
            displayName = "Spotify",
            isPinnedTop = true,
            brandAccentArgb = 0xFF1DB954L,
            fallbackIconRes = R.drawable.ic_service_spotify
        ),
        KnownMusicService(
            packageName = PKG_TIDAL,
            displayName = "TIDAL",
            isPinnedTop = true,
            brandAccentArgb = 0xFFE8E8F0L,
            fallbackIconRes = R.drawable.ic_service_tidal
        )
    )

    val otherKnownMusicServices: List<KnownMusicService> = listOf(
        KnownMusicService(PKG_APPLE_MUSIC, "Apple Music", false, 0xFFFA243CL),
        KnownMusicService(PKG_SOUNDCLOUD, "SoundCloud", false, 0xFFFF5500L),
        KnownMusicService(PKG_DEEZER, "Deezer", false, 0xFFA238FFL),
        KnownMusicService(PKG_AMAZON_MUSIC, "Amazon Music", false, 0xFF25D1DAL),
        KnownMusicService(PKG_QOBUZ, "Qobuz", false, 0xFF2288FFL),
        KnownMusicService(PKG_BANDCAMP, "Bandcamp", false, 0xFF1DA0C3L),
        KnownMusicService(PKG_PANDORA, "Pandora", false, 0xFF3668FFL),
        KnownMusicService(PKG_YOUTUBE, "YouTube", false, 0xFFFF0000L),
        KnownMusicService(PKG_POWERAMP, "Poweramp", false, 0xFF42C767L),
        KnownMusicService(PKG_VLC, "VLC", false, 0xFFFF8800L),
        KnownMusicService(PKG_AUDIBLE, "Audible", false, 0xFFF7991CL),
        KnownMusicService(PKG_POCKET_CASTS, "Pocket Casts", false, 0xFFF43E37L)
    )

    val allKnownServices: List<KnownMusicService> = pinnedTopServices + otherKnownMusicServices

    val knownByPackage: Map<String, KnownMusicService> =
        allKnownServices.associateBy { it.packageName }

    val defaultAllowedPackages: Set<String> =
        pinnedTopServices.map { it.packageName }.toSet()

    fun findByPackage(packageName: String?): KnownMusicService? =
        packageName?.let { knownByPackage[it] }

    @DrawableRes
    fun fallbackBadgeIconFor(packageName: String?): Int {
        return when (packageName) {
            PKG_YT_MUSIC -> R.drawable.ic_service_yt_music
            PKG_SPOTIFY -> R.drawable.ic_service_spotify
            PKG_TIDAL -> R.drawable.ic_service_tidal
            else -> R.drawable.ic_service_music_generic
        }
    }

    @DrawableRes
    fun monochromeGlyphFor(packageName: String?): Int {
        return when (packageName) {
            PKG_YT_MUSIC -> R.drawable.ic_glyph_yt_music
            PKG_SPOTIFY -> R.drawable.ic_glyph_spotify
            PKG_TIDAL -> R.drawable.ic_glyph_tidal
            else -> R.drawable.ic_glyph_music_note
        }
    }
}
