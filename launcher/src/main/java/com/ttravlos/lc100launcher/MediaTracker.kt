package com.ttravlos.lc100launcher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings

class MediaTracker(
    context: Context,
    private val onState: (MediaUiState) -> Unit
) {
    private val appContext = context.applicationContext
    private val sessionManager = appContext.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(appContext, LauncherNotificationListener::class.java)
    private val controllers = mutableMapOf<String, MediaController>()

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        bindControllers(sessions.orEmpty())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishBest()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishBest()
        override fun onSessionDestroyed() = refresh()
    }

    fun start() {
        if (!hasNotificationAccess()) {
            onState(MediaUiState(permissionRequired = true))
            return
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsChanged, listenerComponent)
            refresh()
        } catch (_: SecurityException) {
            onState(MediaUiState(permissionRequired = true))
        }
    }

    fun stop() {
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged) }
        controllers.values.forEach { runCatching { it.unregisterCallback(controllerCallback) } }
        controllers.clear()
    }

    fun refresh() {
        if (!hasNotificationAccess()) {
            onState(MediaUiState(permissionRequired = true))
            return
        }
        try {
            bindControllers(sessionManager.getActiveSessions(listenerComponent))
        } catch (_: SecurityException) {
            onState(MediaUiState(permissionRequired = true))
        }
    }

    private fun bindControllers(sessions: List<MediaController>) {
        controllers.values.forEach { runCatching { it.unregisterCallback(controllerCallback) } }
        controllers.clear()
        sessions.forEach { controller ->
            controllers[controller.sessionToken.toString()] = controller
            controller.registerCallback(controllerCallback)
        }
        publishBest()
    }

    private fun publishBest() {
        val controller = controllers.values.sortedByDescending { playbackRank(it.playbackState?.state) }.firstOrNull()
        if (controller == null) {
            onState(MediaUiState())
            return
        }
        val metadata = controller.metadata
        val title = metadata?.getText(MediaMetadata.METADATA_KEY_TITLE)?.toString()
            ?: metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.toString()
            ?: "Unknown title"
        val artist = metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST)?.toString()
            ?: metadata?.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.toString()
            ?: appLabel(controller.packageName)
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        onState(MediaUiState(title = title, artist = artist, appName = appLabel(controller.packageName), playing = playing))
    }

    private fun appLabel(packageName: String): String = runCatching {
        val info = appContext.packageManager.getApplicationInfo(packageName, 0)
        appContext.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(appContext.contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it)?.packageName == appContext.packageName }
    }

    private fun playbackRank(state: Int?): Int = when (state) {
        PlaybackState.STATE_PLAYING -> 4
        PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> 3
        PlaybackState.STATE_PAUSED -> 2
        PlaybackState.STATE_STOPPED -> 1
        else -> 0
    }
}

data class MediaUiState(
    val title: String? = null,
    val artist: String? = null,
    val appName: String? = null,
    val playing: Boolean = false,
    val permissionRequired: Boolean = false
)
