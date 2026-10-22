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
    private val onReady: () -> Unit,
    private val onChanged: () -> Unit
) {
    private var controller: MediaControllerCompat? = null

    val isConnected: Boolean get() = controller != null
    val metadata: MediaMetadataCompat? get() = controller?.metadata
    val playbackState: PlaybackStateCompat? get() = controller?.playbackState
    private val callback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) = onChanged()
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) = onChanged()
        override fun onSessionDestroyed() { stop() }
    }
    val browser: MediaBrowserCompat = MediaBrowserCompat(context.applicationContext,
        ComponentName(context, MusicBrowserService::class.java),
        object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                handleConnected(context)
            }
            override fun onConnectionFailed() = onChanged()
            override fun onConnectionSuspended() {
                controller?.unregisterCallback(callback)
                controller = null
                onChanged()
            }
        }, null)

    private fun handleConnected(context: Context) {
        val connected = MediaControllerCompat(context, browser.sessionToken)
        controller = connected
        connected.registerCallback(callback)
        onReady()
        onChanged()
    }

    fun play() { controller?.transportControls?.play() }
    fun pause() { controller?.transportControls?.pause() }
    fun stopPlayback() { controller?.transportControls?.stop() }
    fun skipToPrevious() { controller?.transportControls?.skipToPrevious() }
    fun skipToNext() { controller?.transportControls?.skipToNext() }
    fun seekTo(position: Long) { controller?.transportControls?.seekTo(position) }
    fun playFromMediaId(mediaId: String) {
        controller?.transportControls?.playFromMediaId(mediaId, null)
    }

    fun start() { browser.connect() }
    fun stop() {
        controller?.unregisterCallback(callback)
        controller = null
        browser.disconnect()
        onChanged()
    }
}
