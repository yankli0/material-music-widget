package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.widgetPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vinyl_orbit_preferences"
)

class WidgetPreferencesRepository(private val appContext: Context) {

    private object Keys {
        val INITIALIZED = booleanPreferencesKey("defaults_initialized")
        val ALLOWED_PACKAGES = stringSetPreferencesKey("allowed_packages")
        val BUTTON_STYLE = stringPreferencesKey("button_style")
        val EMPTY_VINYL_STYLE = stringPreferencesKey("empty_vinyl_style")
        val CUSTOM_VINYL_VERSION = longPreferencesKey("custom_vinyl_version")
        val CLEAR_ART_WHEN_STOPPED = booleanPreferencesKey("clear_art_when_stopped")
    }

    val settingsFlow: Flow<WidgetSettings> = appContext.widgetPrefsDataStore.data.map { prefs ->
        val initialized = prefs[Keys.INITIALIZED] ?: false
        val allowed = if (initialized) {
            prefs[Keys.ALLOWED_PACKAGES] ?: emptySet()
        } else {
            CuratedMusicApps.defaultAllowedPackages
        }
        WidgetSettings(
            allowedPackages = allowed,
            buttonStyle = ActionButtonStyle.fromKey(prefs[Keys.BUTTON_STYLE]),
            emptyVinylStyle = EmptyVinylStyle.fromKey(prefs[Keys.EMPTY_VINYL_STYLE]),
            customVinylVersion = prefs[Keys.CUSTOM_VINYL_VERSION] ?: 0L,
            clearArtWhenStopped = prefs[Keys.CLEAR_ART_WHEN_STOPPED] ?: false
        )
    }

    suspend fun getCurrentSettings(): WidgetSettings = settingsFlow.first()

    suspend fun togglePackage(packageName: String) {
        appContext.widgetPrefsDataStore.edit { prefs ->
            val initialized = prefs[Keys.INITIALIZED] ?: false
            val current = if (initialized) {
                prefs[Keys.ALLOWED_PACKAGES] ?: emptySet()
            } else {
                CuratedMusicApps.defaultAllowedPackages
            }
            val updated = if (packageName in current) {
                current - packageName
            } else {
                current + packageName
            }
            prefs[Keys.INITIALIZED] = true
            prefs[Keys.ALLOWED_PACKAGES] = updated
        }
    }

    suspend fun resetToDefaultServices() {
        appContext.widgetPrefsDataStore.edit { prefs ->
            prefs[Keys.INITIALIZED] = true
            prefs[Keys.ALLOWED_PACKAGES] = CuratedMusicApps.defaultAllowedPackages
        }
    }

    suspend fun setButtonStyle(style: ActionButtonStyle) {
        appContext.widgetPrefsDataStore.edit { prefs ->
            prefs[Keys.BUTTON_STYLE] = style.key
        }
    }

    suspend fun setEmptyVinylStyle(style: EmptyVinylStyle) {
        appContext.widgetPrefsDataStore.edit { prefs ->
            prefs[Keys.EMPTY_VINYL_STYLE] = style.key
        }
    }

    suspend fun markCustomVinylUpdated() {
        appContext.widgetPrefsDataStore.edit { prefs ->
            prefs[Keys.EMPTY_VINYL_STYLE] = EmptyVinylStyle.CUSTOM_PHOTO.key
            prefs[Keys.CUSTOM_VINYL_VERSION] = System.currentTimeMillis()
        }
    }

    suspend fun setClearArtWhenStopped(enabled: Boolean) {
        appContext.widgetPrefsDataStore.edit { prefs ->
            prefs[Keys.CLEAR_ART_WHEN_STOPPED] = enabled
        }
    }
}

/**
 * Stores a user-picked idle vinyl image file (from zero-permission Android Photo Picker)
 * inside app-private storage so both the app preview and home screen widget can load it safely.
 */
object CustomVinylImageStore {
    private const val FILE_NAME = "custom_empty_vinyl.png"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun loadBitmap(context: Context): Bitmap? {
        val target = file(context)
        if (!target.exists()) return null
        return runCatching { BitmapFactory.decodeFile(target.absolutePath) }.getOrNull()
    }

    suspend fun saveFromUri(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@runCatching false

            val maxDim = 512
            val scaled = if (decoded.width > maxDim || decoded.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height)
                val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            } else {
                decoded
            }

            FileOutputStream(file(context)).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            true
        }.getOrDefault(false)
    }
}

/**
 * Lightweight local snapshot cache for widget RemoteViews updates so the widget
 * can re-render instantaneously after launcher restarts or screen redraws.
 */
object PlaybackSnapshotStore {
    private const val PREFS_NAME = "vinyl_orbit_playback_snapshot"
    private const val KEY_PACKAGE = "pkg"
    private const val KEY_APP_NAME = "app_name"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_IS_PLAYING = "is_playing"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val COVER_FILE_NAME = "last_album_art.png"

    fun save(context: Context, state: ActiveMediaState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PACKAGE, state.packageName)
            .putString(KEY_APP_NAME, state.appName)
            .putString(KEY_TITLE, state.trackTitle)
            .putString(KEY_ARTIST, state.artistName)
            .putBoolean(KEY_IS_PLAYING, state.isPlaying)
            .putLong(KEY_UPDATED_AT, state.updatedAtMillis)
            .apply()

        val coverFile = File(context.filesDir, COVER_FILE_NAME)
        val art = state.albumArt
        if (art != null && !art.isRecycled) {
            runCatching {
                FileOutputStream(coverFile).use { stream ->
                    art.compress(Bitmap.CompressFormat.PNG, 95, stream)
                }
            }
        } else {
            runCatching { if (coverFile.exists()) coverFile.delete() }
        }
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        val coverFile = File(context.filesDir, COVER_FILE_NAME)
        runCatching { if (coverFile.exists()) coverFile.delete() }
    }

    fun load(context: Context): ActiveMediaState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pkg = prefs.getString(KEY_PACKAGE, null) ?: return ActiveMediaState()
        val coverFile = File(context.filesDir, COVER_FILE_NAME)
        val bitmap = if (coverFile.exists()) {
            runCatching { BitmapFactory.decodeFile(coverFile.absolutePath) }.getOrNull()
        } else {
            null
        }
        return ActiveMediaState(
            packageName = pkg,
            appName = prefs.getString(KEY_APP_NAME, null),
            trackTitle = prefs.getString(KEY_TITLE, null),
            artistName = prefs.getString(KEY_ARTIST, null),
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            albumArt = bitmap,
            updatedAtMillis = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
    }
}
