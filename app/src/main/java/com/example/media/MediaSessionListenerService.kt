package com.example.media

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.ActiveMediaState
import com.example.data.CuratedMusicApps
import com.example.data.PlaybackSnapshotStore
import com.example.data.WidgetPreferencesRepository
import com.example.data.WidgetSettings
import com.example.widget.CircleMusicWidgetProvider
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaSessionListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var preferencesRepository: WidgetPreferencesRepository
    private var mediaSessionManager: MediaSessionManager? = null

    private var currentSettings: WidgetSettings = WidgetSettings()
    private val trackedControllers = LinkedHashMap<MediaController, MediaController.Callback>()
    private var evaluationJob: Job? = null

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            onSessionsUpdated(controllers.orEmpty())
        }

    override fun onCreate() {
        super.onCreate()
        activeInstance = WeakReference(this)
        preferencesRepository = WidgetPreferencesRepository(applicationContext)
        mediaSessionManager =
            getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

        serviceScope.launch {
            preferencesRepository.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                refreshActiveSessionsFromSystem()
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeInstance = WeakReference(this)
        registerSessionsListener()
        refreshActiveSessionsFromSystem()
    }

    override fun onListenerDisconnected() {
        unregisterSessionsListener()
        clearCallbacks()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg in currentSettings.allowedPackages) {
            refreshActiveSessionsFromSystem()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg in currentSettings.allowedPackages) {
            refreshActiveSessionsFromSystem()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RECOMPUTE_SESSIONS) {
            refreshActiveSessionsFromSystem()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        if (activeInstance?.get() === this) {
            activeInstance = null
        }
        evaluationJob?.cancel()
        unregisterSessionsListener()
        clearCallbacks()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerSessionsListener() {
        val manager = mediaSessionManager ?: return
        val component = ComponentName(this, MediaSessionListenerService::class.java)
        runCatching {
            manager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            manager.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
        }
    }

    private fun unregisterSessionsListener() {
        val manager = mediaSessionManager ?: return
        runCatching {
            manager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        }
    }

    fun refreshActiveSessionsFromSystem() {
        val manager = mediaSessionManager ?: return
        val component = ComponentName(this, MediaSessionListenerService::class.java)
        val controllers = runCatching {
            manager.getActiveSessions(component)
        }.getOrDefault(emptyList())
        onSessionsUpdated(controllers)
    }

    private fun onSessionsUpdated(controllers: List<MediaController>) {
        clearCallbacks()
        for (controller in controllers) {
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    evaluateAllowedControllers(trackedControllers.keys.toList())
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    evaluateAllowedControllers(trackedControllers.keys.toList())
                }

                override fun onSessionDestroyed() {
                    trackedControllers.remove(controller)
                    evaluateAllowedControllers(trackedControllers.keys.toList())
                }
            }
            runCatching {
                controller.registerCallback(callback)
                trackedControllers[controller] = callback
            }
        }
        evaluateAllowedControllers(controllers)
    }

    private fun evaluateAllowedControllers(controllers: List<MediaController>) {
        val allowed = currentSettings.allowedPackages
        val selected = selectBestAllowedController(controllers, allowed)

        if (selected == null) {
            evaluationJob?.cancel()
            val existing = MediaSessionStateHolder.activeMediaState.value
            val stillAllowed = existing.packageName != null && existing.packageName in allowed
            if (!stillAllowed || currentSettings.clearArtWhenStopped) {
                val emptyState = ActiveMediaState()
                MediaSessionStateHolder.update(emptyState)
                PlaybackSnapshotStore.clear(applicationContext)
            } else if (existing.isPlaying) {
                val pausedState = existing.copy(isPlaying = false)
                MediaSessionStateHolder.update(pausedState)
                PlaybackSnapshotStore.save(applicationContext, pausedState)
            }
            CircleMusicWidgetProvider.updateAllWidgets(applicationContext)
            return
        }

        val pkg = selected.packageName
        val token = selected.sessionToken
        val metadata = selected.metadata
        val isPlaying = MediaPlaybackController.isPlaybackStatePlaying(selected.playbackState)
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: metadata?.description?.title?.toString()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata?.description?.subtitle?.toString()

        // Snapshot active notifications now while on the listener thread
        val notificationsSnapshot = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
        val matchingNotifExtras = findBestNotificationForSession(notificationsSnapshot, pkg, token)?.notification?.extras
        val resolvedTitle = title?.takeIf { it.isNotBlank() }
            ?: matchingNotifExtras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val resolvedArtist = artist?.takeIf { it.isNotBlank() }
            ?: matchingNotifExtras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        // Cancel any previous in-flight artwork load so stale responses never overwrite newer art
        evaluationJob?.cancel()
        evaluationJob = serviceScope.launch {
            val artBitmap = withContext(Dispatchers.IO) {
                extractCoverBitmap(
                    context = applicationContext,
                    metadata = metadata,
                    packageName = pkg,
                    sessionToken = token,
                    notifications = notificationsSnapshot
                )
            }
            val appName = resolveAppName(applicationContext, pkg)
            val previous = MediaSessionStateHolder.activeMediaState.value
            val resolvedArt = artBitmap ?: if (
                previous.packageName == pkg &&
                !resolvedTitle.isNullOrBlank() &&
                previous.trackTitle == resolvedTitle
            ) {
                previous.albumArt
            } else {
                null
            }

            val nextState = ActiveMediaState(
                packageName = pkg,
                appName = appName,
                trackTitle = resolvedTitle,
                artistName = resolvedArtist,
                isPlaying = isPlaying,
                albumArt = resolvedArt,
                updatedAtMillis = System.currentTimeMillis()
            )
            MediaSessionStateHolder.update(nextState)
            PlaybackSnapshotStore.save(applicationContext, nextState)
            CircleMusicWidgetProvider.updateAllWidgets(applicationContext)
        }
    }

    private fun clearCallbacks() {
        for ((controller, callback) in trackedControllers) {
            runCatching { controller.unregisterCallback(callback) }
        }
        trackedControllers.clear()
    }

    companion object {
        const val ACTION_RECOMPUTE_SESSIONS = "com.example.media.ACTION_RECOMPUTE_SESSIONS"
        private var activeInstance: WeakReference<MediaSessionListenerService>? = null

        fun triggerRecompute(context: Context) {
            if (!NotificationAccessHelper.isNotificationListenerEnabled(context)) return
            val liveService = activeInstance?.get()
            if (liveService != null) {
                liveService.serviceScope.launch {
                    liveService.currentSettings = WidgetPreferencesRepository(context.applicationContext)
                        .getCurrentSettings()
                    liveService.refreshActiveSessionsFromSystem()
                }
                return
            }
            // Fallback: request rebind and synchronously poll active MediaSessions
            MediaPlaybackController.requestRebindIfEnabled(context)
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                syncWithoutBoundService(context.applicationContext)
            }
        }

        private suspend fun syncWithoutBoundService(context: Context) {
            val settings = WidgetPreferencesRepository(context).getCurrentSettings()
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return
            val component = ComponentName(context, MediaSessionListenerService::class.java)
            val controllers = runCatching { manager.getActiveSessions(component) }.getOrDefault(emptyList())
            val selected = selectBestAllowedController(controllers, settings.allowedPackages)

            if (selected == null) {
                val existing = MediaSessionStateHolder.activeMediaState.value
                val stillAllowed = existing.packageName != null && existing.packageName in settings.allowedPackages
                if (!stillAllowed || settings.clearArtWhenStopped) {
                    MediaSessionStateHolder.update(ActiveMediaState())
                    PlaybackSnapshotStore.clear(context)
                }
                CircleMusicWidgetProvider.updateAllWidgets(context)
                return
            }

            val pkg = selected.packageName
            val metadata = selected.metadata
            val isPlaying = MediaPlaybackController.isPlaybackStatePlaying(selected.playbackState)
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: metadata?.description?.title?.toString()
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: metadata?.description?.subtitle?.toString()

            val artBitmap = extractCoverBitmap(
                context = context,
                metadata = metadata,
                packageName = pkg,
                sessionToken = selected.sessionToken,
                notifications = emptyList()
            )
            val nextState = ActiveMediaState(
                packageName = pkg,
                appName = resolveAppName(context, pkg),
                trackTitle = title,
                artistName = artist,
                isPlaying = isPlaying,
                albumArt = artBitmap,
                updatedAtMillis = System.currentTimeMillis()
            )
            MediaSessionStateHolder.update(nextState)
            PlaybackSnapshotStore.save(context, nextState)
            CircleMusicWidgetProvider.updateAllWidgets(context)
        }

        internal fun selectBestAllowedController(
            controllers: List<MediaController>,
            allowedPackages: Set<String>
        ): MediaController? {
            val allowedControllers = controllers.filter { it.packageName in allowedPackages }
            if (allowedControllers.isEmpty()) return null

            val playing = allowedControllers.filter {
                MediaPlaybackController.isPlaybackStatePlaying(it.playbackState)
            }
            if (playing.isNotEmpty()) {
                return playing.maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
            }

            val withMetadata = allowedControllers.filter { controller ->
                controller.metadata != null && controller.playbackState?.state != PlaybackState.STATE_NONE
            }
            if (withMetadata.isNotEmpty()) {
                return withMetadata.maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
            }

            return allowedControllers.firstOrNull()
        }

        internal fun extractCoverBitmap(
            context: Context,
            metadata: MediaMetadata?,
            packageName: String,
            sessionToken: MediaSession.Token?,
            notifications: List<StatusBarNotification>
        ): Bitmap? {
            // 1. Direct MediaMetadata bitmaps (including MediaDescription iconBitmap used by browsers)
            val rawBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                ?: metadata?.description?.iconBitmap
            if (rawBitmap != null && !rawBitmap.isRecycled) {
                scaleDownForWidget(rawBitmap)?.let { return it }
            }

            // 2. Matching Media Notification artwork (critical for Chrome which often posts
            //    multiple non-media notifications alongside its MediaStyle playback notification!)
            val prioritized = prioritizeNotificationsForSession(notifications, packageName, sessionToken)
            for (sbn in prioritized) {
                extractBitmapFromNotification(context, sbn.notification)?.let { return it }
            }

            // 3. Artwork URIs from MediaMetadata (content://, android.resource://, and http/https for Chrome!)
            val uriCandidates = listOfNotNull(
                metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
                metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI),
                metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
                metadata?.description?.iconUri?.toString()
            ).distinct()

            for (uriString in uriCandidates) {
                if (uriString.isBlank()) continue
                val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: continue
                when (uri.scheme?.lowercase()) {
                    "content", "android.resource" -> {
                        val decoded = runCatching {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        }.getOrNull()
                        if (decoded != null) {
                            scaleDownForWidget(decoded)?.let { return it }
                        }
                    }

                    "http", "https" -> {
                        downloadBitmapFromUrl(uriString)?.let { downloaded ->
                            scaleDownForWidget(downloaded)?.let { return it }
                        }
                    }
                }
            }

            return null
        }

        private fun findBestNotificationForSession(
            notifications: List<StatusBarNotification>,
            packageName: String,
            sessionToken: MediaSession.Token?
        ): StatusBarNotification? =
            prioritizeNotificationsForSession(notifications, packageName, sessionToken).firstOrNull()

        private fun prioritizeNotificationsForSession(
            notifications: List<StatusBarNotification>,
            packageName: String,
            sessionToken: MediaSession.Token?
        ): List<StatusBarNotification> {
            return notifications
                .filter { it.packageName == packageName }
                .sortedByDescending { sbn ->
                    var score = 0
                    val extras = sbn.notification.extras
                    val notifToken = getMediaSessionTokenCompat(extras)
                    if (sessionToken != null && notifToken == sessionToken) score += 100
                    if (notifToken != null) score += 50
                    val template = extras?.getString(Notification.EXTRA_TEMPLATE).orEmpty()
                    if (template.contains("MediaStyle", ignoreCase = true)) score += 30
                    if (sbn.notification.getLargeIcon() != null) score += 10
                    score
                }
        }

        private fun getMediaSessionTokenCompat(extras: android.os.Bundle?): MediaSession.Token? {
            if (extras == null) return null
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
                }
            }.getOrNull()
        }

        private fun extractBitmapFromNotification(
            context: Context,
            notification: Notification
        ): Bitmap? {
            val extras = notification.extras

            // Check EXTRA_PICTURE (used by some media/browser notifications)
            val pictureBitmap = getParcelableBitmapCompat(extras, Notification.EXTRA_PICTURE)
            if (pictureBitmap != null && !pictureBitmap.isRecycled) {
                scaleDownForWidget(pictureBitmap)?.let { return it }
            }

            // Check large icon via Icon API (handles bitmap, adaptive bitmap, resource, content URI)
            val largeIcon: Icon? = notification.getLargeIcon()
            if (largeIcon != null) {
                loadBitmapFromIcon(context, largeIcon)?.let { return it }
            }

            // Check EXTRA_LARGE_ICON_BIG and EXTRA_LARGE_ICON (can be Bitmap or Icon)
            for (key in listOf(Notification.EXTRA_LARGE_ICON_BIG, Notification.EXTRA_LARGE_ICON)) {
                val bmp = getParcelableBitmapCompat(extras, key)
                if (bmp != null && !bmp.isRecycled) {
                    scaleDownForWidget(bmp)?.let { return it }
                }
                val icon = getParcelableIconCompat(extras, key)
                if (icon != null) {
                    loadBitmapFromIcon(context, icon)?.let { return it }
                }
            }

            return null
        }

        private fun getParcelableBitmapCompat(extras: android.os.Bundle?, key: String): Bitmap? {
            if (extras == null) return null
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable(key, Bitmap::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable<android.os.Parcelable>(key) as? Bitmap
                }
            }.getOrNull()
        }

        private fun getParcelableIconCompat(extras: android.os.Bundle?, key: String): Icon? {
            if (extras == null) return null
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable(key, Icon::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable<android.os.Parcelable>(key) as? Icon
                }
            }.getOrNull()
        }

        private fun loadBitmapFromIcon(context: Context, icon: Icon): Bitmap? {
            val drawable = runCatching { icon.loadDrawable(context) }.getOrNull() ?: return null
            if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
                return scaleDownForWidget(drawable.bitmap)
            }
            val width = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(480) ?: 384
            val height = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(480) ?: 384
            return runCatching {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap
            }.getOrNull()
        }

        private fun downloadBitmapFromUrl(urlString: String): Bitmap? {
            return runCatching {
                val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4_000
                    readTimeout = 4_000
                    instanceFollowRedirects = true
                    doInput = true
                }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    connection.inputStream.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }

        private fun scaleDownForWidget(source: Bitmap, maxDim: Int = 480): Bitmap? {
            // Convert Android HARDWARE bitmaps safely to software ARGB_8888 before scaling/drawing
            val software = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                source.config == Bitmap.Config.HARDWARE
            ) {
                source.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                source
            } ?: return null

            val width = software.width
            val height = software.height
            if (width <= 0 || height <= 0) return null

            if (width <= maxDim && height <= maxDim) {
                return software.copy(Bitmap.Config.ARGB_8888, false) ?: software
            }
            val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
            val targetW = (width * ratio).toInt().coerceAtLeast(1)
            val targetH = (height * ratio).toInt().coerceAtLeast(1)
            return runCatching {
                Bitmap.createScaledBitmap(software, targetW, targetH, true)
            }.getOrNull()
        }

        private fun resolveAppName(context: Context, packageName: String): String {
            CuratedMusicApps.findByPackage(packageName)?.let { return it.displayName }
            return runCatching {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(packageName)
        }
    }
}
