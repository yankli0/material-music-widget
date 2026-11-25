package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import com.example.R
import com.example.data.ActiveMediaState
import com.example.data.PlaybackSnapshotStore
import com.example.data.WidgetPreferencesRepository
import com.example.data.WidgetSettings
import com.example.media.MediaPlaybackController
import com.example.media.MediaSessionStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CircleMusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                renderWidgets(context.applicationContext, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                renderSingleWidget(context.applicationContext, appWidgetManager, appWidgetId, newOptions)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MediaPlaybackController.requestRebindIfEnabled(context.applicationContext)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_TOGGLE_PLAY_STOP -> {
                val pendingResult = goAsync()
                widgetScope.launch {
                    try {
                        MediaPlaybackController.togglePlayStop(appContext)
                        updateAllWidgetsInternal(appContext)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_OPEN_SOURCE_APP -> {
                val pkg = intent.getStringExtra(EXTRA_SOURCE_PACKAGE)
                MediaPlaybackController.openSourceApp(appContext, pkg)
            }

            ACTION_REFRESH_WIDGET -> {
                val pendingResult = goAsync()
                widgetScope.launch {
                    try {
                        updateAllWidgetsInternal(appContext)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_PLAY_STOP = "com.example.widget.ACTION_TOGGLE_PLAY_STOP"
        const val ACTION_OPEN_SOURCE_APP = "com.example.widget.ACTION_OPEN_SOURCE_APP"
        const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH_WIDGET"
        private const val EXTRA_SOURCE_PACKAGE = "extra_source_package"

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            widgetScope.launch {
                updateAllWidgetsInternal(appContext)
            }
        }

        private suspend fun updateAllWidgetsInternal(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CircleMusicWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            renderWidgets(context, manager, ids)
        }

        private suspend fun renderWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            for (appWidgetId in appWidgetIds) {
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                renderSingleWidget(context, appWidgetManager, appWidgetId, options)
            }
        }

        private suspend fun renderSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle
        ) {
            val settings = WidgetPreferencesRepository(context).getCurrentSettings()
            val memoryState = MediaSessionStateHolder.activeMediaState.value
            val rawState = if (memoryState.hasActiveTrack) {
                memoryState
            } else {
                PlaybackSnapshotStore.load(context)
            }

            val filteredState = if (rawState.packageName != null && rawState.packageName in settings.allowedPackages) {
                if (settings.clearArtWhenStopped && !rawState.isPlaying) {
                    ActiveMediaState()
                } else {
                    rawState
                }
            } else {
                ActiveMediaState()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val sizes: List<SizeF> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    options.getParcelableArrayList(
                        AppWidgetManager.OPTION_APPWIDGET_SIZES,
                        SizeF::class.java
                    ).orEmpty()
                } else {
                    @Suppress("DEPRECATION")
                    options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                        .orEmpty()
                }

                if (sizes.isNotEmpty()) {
                    val viewMapping = sizes.associateWith { size ->
                        buildSizedRemoteViews(
                            context = context,
                            widthDp = size.width,
                            heightDp = size.height,
                            settings = settings,
                            filteredState = filteredState
                        )
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, RemoteViews(viewMapping))
                    return
                }
            }

            val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160).toFloat()
            val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160).toFloat()
            val views = buildSizedRemoteViews(
                context = context,
                widthDp = if (minW > 0f) minW else 160f,
                heightDp = if (minH > 0f) minH else 160f,
                settings = settings,
                filteredState = filteredState
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun buildSizedRemoteViews(
            context: Context,
            widthDp: Float,
            heightDp: Float,
            settings: WidgetSettings,
            filteredState: ActiveMediaState
        ): RemoteViews {
            val density = context.resources.displayMetrics.density
            // Fill nearly the full square widget footprint while keeping diagonal rim buttons unclipped
            val sideDp = (minOf(widthDp, heightDp) - 2f).coerceIn(112f, 480f)
            val discDp = sideDp * 0.94f
            val radiusDp = discDp / 2f

            // Bottom-left Play/Stop button scaled smoothly with widget size & locked to 135 deg rim
            val playBtnDp = (discDp * 0.31f).coerceIn(46f, 86f)
            val playCenterX = sideDp * 0.50f - radiusDp * 0.636f
            val playMarginDp = (playCenterX - playBtnDp / 2f).coerceAtLeast(0f)

            // Top-right Service badge scaled smoothly & locked to -45 deg rim (diagonal mirror)
            val badgeDp = (discDp * 0.27f).coerceIn(40f, 74f)
            val badgeCenterX = sideDp * 0.50f + radiusDp * 0.636f
            val badgeMarginDp = (sideDp - (badgeCenterX + badgeDp / 2f)).coerceAtLeast(0f)

            val discPx = (discDp * density).toInt().coerceIn(256, 680)
            val playPx = (playBtnDp * density).toInt().coerceIn(112, 240)
            val badgePx = (badgeDp * density).toInt().coerceIn(96, 200)

            val albumBitmap = WidgetBitmapRenderer.renderCircularAlbumArt(
                context = context,
                albumArt = filteredState.albumArt,
                emptyVinylStyle = settings.emptyVinylStyle,
                sizePx = discPx
            )
            val playStopBitmap = WidgetBitmapRenderer.renderPlayStopButton(
                context = context,
                isPlaying = filteredState.isPlaying,
                buttonStyle = settings.buttonStyle,
                sizePx = playPx
            )
            val serviceBadgeBitmap = WidgetBitmapRenderer.renderServiceBadge(
                context = context,
                packageName = filteredState.packageName,
                sizePx = badgePx
            )

            val toggleIntent = Intent(context, CircleMusicWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_PLAY_STOP
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val openSourceIntent = Intent(context, CircleMusicWidgetProvider::class.java).apply {
                action = ACTION_OPEN_SOURCE_APP
                putExtra(EXTRA_SOURCE_PACKAGE, filteredState.packageName)
            }
            val openSourcePendingIntent = PendingIntent.getBroadcast(
                context,
                102,
                openSourceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPauseCd = if (filteredState.isPlaying) {
                context.getString(R.string.cd_stop_button)
            } else {
                context.getString(R.string.cd_play_button)
            }

            return RemoteViews(context.packageName, R.layout.widget_circle_player).apply {
                setImageViewBitmap(R.id.widget_album_art, albumBitmap)
                setImageViewBitmap(R.id.widget_play_stop_button, playStopBitmap)
                setImageViewBitmap(R.id.widget_service_badge, serviceBadgeBitmap)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setViewLayoutWidth(R.id.widget_circle_anchor, sideDp, TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutHeight(R.id.widget_circle_anchor, sideDp, TypedValue.COMPLEX_UNIT_DIP)

                    setViewLayoutWidth(R.id.widget_album_art, discDp, TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutHeight(R.id.widget_album_art, discDp, TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutMargin(R.id.widget_album_art, RemoteViews.MARGIN_START, 0f, TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutMargin(R.id.widget_album_art, RemoteViews.MARGIN_END, 0f, TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutMargin(R.id.widget_album_art, RemoteViews.MARGIN_TOP, 0f, TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutMargin(R.id.widget_album_art, RemoteViews.MARGIN_BOTTOM, 0f, TypedValue.COMPLEX_UNIT_DIP)

                    setViewLayoutWidth(R.id.widget_play_stop_button, playBtnDp, TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutHeight(R.id.widget_play_stop_button, playBtnDp, TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutMargin(
                        R.id.widget_play_stop_button,
                        RemoteViews.MARGIN_START,
                        playMarginDp,
                        TypedValue.COMPLEX_UNIT_DIP
                    )
                    setViewLayoutMargin(
                        R.id.widget_play_stop_button,
                        RemoteViews.MARGIN_BOTTOM,
                        playMarginDp,
                        TypedValue.COMPLEX_UNIT_DIP
                    )

                    setViewLayoutWidth(R.id.widget_service_badge, badgeDp, TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutHeight(R.id.widget_service_badge, badgeDp, TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutMargin(
                        R.id.widget_service_badge,
                        RemoteViews.MARGIN_TOP,
                        badgeMarginDp,
                        TypedValue.COMPLEX_UNIT_DIP
                    )
                    setViewLayoutMargin(
                        R.id.widget_service_badge,
                        RemoteViews.MARGIN_END,
                        badgeMarginDp,
                        TypedValue.COMPLEX_UNIT_DIP
                    )
                }

                setContentDescription(R.id.widget_play_stop_button, playPauseCd)
                setOnClickPendingIntent(R.id.widget_play_stop_button, togglePendingIntent)
                setOnClickPendingIntent(R.id.widget_album_art, openSourcePendingIntent)
                setOnClickPendingIntent(R.id.widget_service_badge, openSourcePendingIntent)
            }
        }

        fun requestPinWidget(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (!appWidgetManager.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(context, CircleMusicWidgetProvider::class.java)
            return runCatching {
                appWidgetManager.requestPinAppWidget(provider, null, null)
            }.getOrDefault(false)
        }
    }
}
