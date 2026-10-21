package com.autoai.media.qqmusic.media.session

import android.content.ComponentName
import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.autoai.media.qqmusic.service.MusicBrowserService

/** 页面只连接媒体会话；停止页面连接不会停止服务中的播放器。 */
internal class MediaSessionConnection(
    context: Context,
    private val onReady: (MediaControllerCompat) -> Unit,
    private val onChanged: (MediaControllerCompat?) -> Unit
) {
    var controller: MediaControllerCompat? = null
        private set
    private val callback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) = onChanged(controller)
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) = onChanged(controller)
        override fun onSessionDestroyed() { stop() }
    }
    val browser: MediaBrowserCompat = MediaBrowserCompat(context.applicationContext,
        ComponentName(context, MusicBrowserService::class.java),
        object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                handleConnected(context)
            }
            override fun onConnectionFailed() = onChanged(null)
            override fun onConnectionSuspended() {
                controller?.unregisterCallback(callback)
                controller = null
                onChanged(null)
            }
        }, null)

    private fun handleConnected(context: Context) {
        val connected = MediaControllerCompat(context, browser.sessionToken)
        controller = connected
        connected.registerCallback(callback)
        onReady(connected)
        onChanged(connected)
    }

    fun start() { browser.connect() }
    fun stop() {
        controller?.unregisterCallback(callback)
        controller = null
        browser.disconnect()
        onChanged(null)
    }
}
