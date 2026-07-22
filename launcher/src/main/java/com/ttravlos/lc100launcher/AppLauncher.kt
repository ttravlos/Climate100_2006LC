package com.ttravlos.lc100launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

object AppLauncher {

    fun launchPackage(
        context: Context,
        packageNames: List<String>,
        displayName: String
    ) {
        for (packageName in packageNames) {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
        }

        showMessage(context, "$displayName is not installed")
    }

    fun openNavigation(context: Context) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=")
        ).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            launchPackage(
                context,
                listOf("com.google.android.apps.maps", "com.waze"),
                "Navigation app"
            )
        }
    }

    fun openMusic(context: Context) {
        if (launchAppByLabel(context, "YouTube Music")) {
            return
        }

        launchPackage(
            context,
            listOf(
                "com.google.android.apps.youtube.music",
                "com.spotify.music",
                "com.google.android.music"
            ),
            "Music app"
        )
    }

    fun openClimate(context: Context) {
        launchPackage(
            context,
            listOf("com.ttravlos.lc100climate.dev"),
            "Climate100 Dev"
        )
    }

    fun openPhone(context: Context) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(
                "com.syu.bt",
                "com.syu.bt.BtAct"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            launchPackage(
                context,
                listOf("com.syu.bt"),
                "Bluetooth phone app"
            )
        }
    }

    fun openHomeAssistant(context: Context) {
        launchPackage(
            context,
            listOf(
                "io.homeassistant.companion.android",
                "io.homeassistant.companion.android.minimal"
            ),
            "Home Assistant"
        )
    }

    fun openApps(context: Context) {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            showMessage(context, "Settings is unavailable")
        }
    }

    private fun launchAppByLabel(context: Context, targetLabel: String): Boolean {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities = packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )

        val match = activities.firstOrNull { resolveInfo ->
            resolveInfo.loadLabel(packageManager)
                ?.toString()
                ?.equals(targetLabel, ignoreCase = true) == true
        } ?: return false

        val activityInfo = match.activityInfo
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(activityInfo.packageName, activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
        return true
    }

    private fun showMessage(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
