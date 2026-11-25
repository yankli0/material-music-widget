package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DiscoveredDeviceApp(
    val packageName: String,
    val displayName: String,
    val isInstalled: Boolean,
    val isPinnedTop: Boolean,
    val isMusicApp: Boolean,
    val brandAccentArgb: Long?
)

class InstalledAppsRepository(private val appContext: Context) {

    private val iconCache = mutableMapOf<String, Drawable?>()

    suspend fun loadDeviceApps(): List<DiscoveredDeviceApp> = withContext(Dispatchers.IO) {
        val pm = appContext.packageManager
        val selfPackage = appContext.packageName

        val mediaPackages = mutableSetOf<String>()
        // 1. Discover MediaBrowserService apps
        val mediaBrowserIntent = Intent("android.media.browse.MediaBrowserService")
        queryServicesCompat(pm, mediaBrowserIntent).forEach { resolveInfo ->
            resolveInfo.serviceInfo?.packageName?.let { mediaPackages.add(it) }
        }
        // 2. Discover MEDIA_BUTTON receivers
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        queryReceiversCompat(pm, mediaButtonIntent).forEach { resolveInfo ->
            resolveInfo.activityInfo?.packageName?.let { mediaPackages.add(it) }
        }
        // 3. Discover APP_MUSIC activities
        val appMusicIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
        }
        queryActivitiesCompat(pm, appMusicIntent).forEach { resolveInfo ->
            resolveInfo.activityInfo?.packageName?.let { mediaPackages.add(it) }
        }

        // 4. Discover all launchable apps (Play-policy compliant via <queries> MAIN/LAUNCHER)
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launcherActivities = queryActivitiesCompat(pm, launcherIntent)
        val installedByPackage = LinkedHashMap<String, String>()
        for (info in launcherActivities) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == selfPackage) continue
            if (!installedByPackage.containsKey(pkg)) {
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                installedByPackage[pkg] = label.ifEmpty { pkg }
            }
        }

        // Also check audio category on API 26+ for installed apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            for (pkg in installedByPackage.keys) {
                val appInfo = getApplicationInfoOrNull(pm, pkg)
                if (appInfo?.category == ApplicationInfo.CATEGORY_AUDIO) {
                    mediaPackages.add(pkg)
                }
            }
        }

        // Build final list: Pinned top services ALWAYS first (whether installed or not)
        val result = mutableListOf<DiscoveredDeviceApp>()
        val addedPackages = mutableSetOf<String>()

        for (pinned in CuratedMusicApps.pinnedTopServices) {
            val installedAppInfo = getApplicationInfoOrNull(pm, pinned.packageName)
            val isInstalled = installedAppInfo != null || installedByPackage.containsKey(pinned.packageName)
            val label = installedByPackage[pinned.packageName]
                ?: installedAppInfo?.let { pm.getApplicationLabel(it).toString() }
                ?: pinned.displayName
            result.add(
                DiscoveredDeviceApp(
                    packageName = pinned.packageName,
                    displayName = label,
                    isInstalled = isInstalled,
                    isPinnedTop = true,
                    isMusicApp = true,
                    brandAccentArgb = pinned.brandAccentArgb
                )
            )
            addedPackages.add(pinned.packageName)
        }

        // Next: other known music apps that are installed on the device
        for (known in CuratedMusicApps.otherKnownMusicServices) {
            if (known.packageName in addedPackages) continue
            val installedAppInfo = getApplicationInfoOrNull(pm, known.packageName)
            val isInstalled = installedAppInfo != null || installedByPackage.containsKey(known.packageName)
            if (isInstalled) {
                val label = installedByPackage[known.packageName]
                    ?: installedAppInfo?.let { pm.getApplicationLabel(it).toString() }
                    ?: known.displayName
                result.add(
                    DiscoveredDeviceApp(
                        packageName = known.packageName,
                        displayName = label,
                        isInstalled = true,
                        isPinnedTop = false,
                        isMusicApp = true,
                        brandAccentArgb = known.brandAccentArgb
                    )
                )
                addedPackages.add(known.packageName)
            }
        }

        // Next: remaining launchable apps on device (media apps first, then alphabetical)
        val remaining = installedByPackage.entries
            .filter { (pkg, _) -> pkg !in addedPackages }
            .map { (pkg, label) ->
                val known = CuratedMusicApps.findByPackage(pkg)
                val isMedia = pkg in mediaPackages || known != null
                DiscoveredDeviceApp(
                    packageName = pkg,
                    displayName = label,
                    isInstalled = true,
                    isPinnedTop = false,
                    isMusicApp = isMedia,
                    brandAccentArgb = known?.brandAccentArgb
                )
            }
            .sortedWith(
                compareByDescending<DiscoveredDeviceApp> { it.isMusicApp }
                    .thenBy { it.displayName.lowercase() }
            )

        result.addAll(remaining)
        result
    }

    suspend fun loadAppIcon(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        synchronized(iconCache) {
            if (iconCache.containsKey(packageName)) {
                return@withContext iconCache[packageName]
            }
        }
        val drawable = runCatching {
            appContext.packageManager.getApplicationIcon(packageName)
        }.getOrNull()
        synchronized(iconCache) {
            iconCache[packageName] = drawable
        }
        drawable
    }

    private fun getApplicationInfoOrNull(pm: PackageManager, pkg: String): ApplicationInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
        }.getOrNull()
    }

    private fun queryActivitiesCompat(pm: PackageManager, intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    private fun queryServicesCompat(pm: PackageManager, intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, 0)
        }

    private fun queryReceiversCompat(pm: PackageManager, intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryBroadcastReceivers(intent, 0)
        }
}
