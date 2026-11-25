package com.example.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.ActiveMediaState
import com.example.data.PlaybackSnapshotStore
import com.example.data.WidgetPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MediaSessionStateHolder {
    private val _activeMediaState = MutableStateFlow(ActiveMediaState())
    val activeMediaState: StateFlow<ActiveMediaState> = _activeMediaState.asStateFlow()

    fun update(state: ActiveMediaState) {
        _activeMediaState.value = state
    }

    fun hydrateFromSnapshotIfEmpty(context: Context) {
        if (!_activeMediaState.value.hasActiveTrack) {
            val cached = PlaybackSnapshotStore.load(context)
            if (cached.hasActiveTrack) {
                _activeMediaState.value = cached
            }
        }
    }
}

object NotificationAccessHelper {
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return context.packageName in enabledPackages
    }

    fun createNotificationAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

object MediaPlaybackController {

    fun isPlaybackStatePlaying(state: PlaybackState?): Boolean {
        val code = state?.state ?: return false
        return code == PlaybackState.STATE_PLAYING ||
            code == PlaybackState.STATE_BUFFERING ||
            code == PlaybackState.STATE_CONNECTING
    }

    /**
     * Toggles play/stop on the active allowed media session.
     * Returns true if a live session transport command was dispatched.
     */
    suspend fun togglePlayStop(context: Context): Boolean {
        val appContext = context.applicationContext
        val settings = WidgetPreferencesRepository(appContext).getCurrentSettings()
        if (!NotificationAccessHelper.isNotificationListenerEnabled(appContext)) {
            openAppOrSettings(appContext, null)
            return false
        }

        val mediaSessionManager =
            appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return false

        val component = ComponentName(appContext, MediaSessionListenerService::class.java)
        val controllers = runCatching {
            mediaSessionManager.getActiveSessions(component)
        }.getOrDefault(emptyList())

        val allowedControllers = controllers.filter { it.packageName in settings.allowedPackages }
        val currentState = MediaSessionStateHolder.activeMediaState.value
            .takeIf { it.hasActiveTrack }
            ?: PlaybackSnapshotStore.load(appContext)

        val target: MediaController? =
            allowedControllers.firstOrNull { isPlaybackStatePlaying(it.playbackState) }
                ?: allowedControllers.firstOrNull { it.packageName == currentState.packageName }
                ?: allowedControllers.firstOrNull()

        if (target != null) {
            val currentlyPlaying = isPlaybackStatePlaying(target.playbackState)
            if (currentlyPlaying) {
                target.transportControls.pause()
            } else {
                target.transportControls.play()
            }
            return true
        }

        // No active media session from an allowed app — open the last allowed service or Vinyl Orbit
        val fallbackPackage = currentState.packageName?.takeIf { it in settings.allowedPackages }
            ?: settings.allowedPackages.firstOrNull { isLaunchable(appContext, it) }
        openAppOrSettings(appContext, fallbackPackage)
        return false
    }

    fun openSourceApp(context: Context, packageName: String?) {
        openAppOrSettings(context.applicationContext, packageName)
    }

    private fun openAppOrSettings(context: Context, packageName: String?) {
        val launchIntent = packageName?.let { pkg ->
            runCatching { context.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()
        }
        val intent = (launchIntent ?: Intent(context, MainActivity::class.java)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun isLaunchable(context: Context, pkg: String): Boolean {
        return runCatching {
            context.packageManager.getLaunchIntentForPackage(pkg) != null
        }.getOrDefault(false)
    }

    fun requestRebindIfEnabled(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            NotificationAccessHelper.isNotificationListenerEnabled(context)
        ) {
            runCatching {
                val component = ComponentName(context, MediaSessionListenerService::class.java)
                android.service.notification.NotificationListenerService.requestRebind(component)
            }
        }
    }
}
