package com.example.newapp

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process

object AppDetector {

    /**
     * Check whether Usage Access permission is enabled.
     */
    fun hasUsageAccess(context: Context): Boolean {

        val appOps =
            context.getSystemService(
                Context.APP_OPS_SERVICE
            ) as AppOpsManager

        val mode =
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )

        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Finds the most recently used real application.
     *
     * Newapp, Android launcher and Android/system
     * applications are ignored.
     */
    fun getForegroundApp(
        context: Context
    ): String? {

        if (!hasUsageAccess(context)) {
            return null
        }

        val usageStatsManager =
            context.getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager

        val currentTime =
            System.currentTimeMillis()

        /*
         * Look back over the last 30 minutes.
         */
        val startTime =
            currentTime - (30 * 60 * 1000L)

        val usageEvents =
            usageStatsManager.queryEvents(
                startTime,
                currentTime
            )

        val event =
            UsageEvents.Event()

        var latestPackage: String? = null
        var latestTimestamp = 0L

        while (usageEvents.hasNextEvent()) {

            usageEvents.getNextEvent(event)

            /*
             * We only care about activities that
             * became visible/resumed.
             */
            if (
                event.eventType !=
                UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                continue
            }

            val packageName =
                event.packageName

            if (packageName.isNullOrBlank()) {
                continue
            }

            /*
             * Ignore Newapp itself.
             */
            if (
                packageName ==
                context.packageName
            ) {
                continue
            }

            /*
             * Ignore the Android launcher.
             *
             * Samsung devices commonly use:
             * com.sec.android.app.launcher
             */
            if (
                isLauncher(
                    context,
                    packageName
                )
            ) {
                continue
            }

            /*
             * Ignore Android system packages.
             */
            if (
                isSystemPackage(
                    context,
                    packageName
                )
            ) {
                continue
            }

            /*
             * Keep the most recent valid application.
             */
            if (
                event.timeStamp >
                latestTimestamp
            ) {

                latestTimestamp =
                    event.timeStamp

                latestPackage =
                    packageName
            }
        }

        return latestPackage
    }

    /**
     * Check whether the package is the current
     * Android Home/Launcher application.
     */
    private fun isLauncher(
        context: Context,
        packageName: String
    ): Boolean {

        return try {

            val intent =
                Intent(
                    Intent.ACTION_MAIN
                ).apply {
                    addCategory(
                        Intent.CATEGORY_HOME
                    )
                }

            val resolveInfo =
                context.packageManager
                    .resolveActivity(
                        intent,
                        PackageManager.MATCH_DEFAULT_ONLY
                    )

            val launcherPackage =
                resolveInfo
                    ?.activityInfo
                    ?.packageName

            packageName == launcherPackage

        } catch (_: Exception) {

            /*
             * Samsung launcher fallback.
             */
            packageName ==
                    "com.sec.android.app.launcher"
        }
    }

    /**
     * Ignore Android/system packages.
     */
    private fun isSystemPackage(
        context: Context,
        packageName: String
    ): Boolean {

        /*
         * Explicit system packages that should never
         * become the target application.
         */
        if (
            packageName == "android" ||
            packageName == "com.android.settings" ||
            packageName.startsWith("com.android.systemui")
        ) {
            return true
        }

        return try {

            val applicationInfo =
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.GET_META_DATA
                )

            /*
             * Ignore applications marked as
             * Android system applications.
             */
            (
                    applicationInfo.flags and
                            android.content.pm.ApplicationInfo.FLAG_SYSTEM
                    ) != 0

        } catch (_: Exception) {

            // Package not visible (Android 11+ package visibility) is NOT a system app.
            false
        }
    }
}