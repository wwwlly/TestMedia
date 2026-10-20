package com.autoai.media.qqmusic.service

import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.autoai.media.qqmusic.dialog.MusicCatalog

class MusicBrowserService : MediaBrowserServiceCompat() {
    private lateinit var mediaSession: MediaSessionCompat
    private var currentIndex = 0

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "TestMediaMusicSession").apply {
            setCallback(sessionCallback)
            isActive = true
        }
        sessionToken = mediaSession.sessionToken
        updateCurrentSong()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_NOT_STICKY
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot = BrowserRoot(MEDIA_ROOT, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId == MEDIA_ROOT) {
            result.sendResult(MusicCatalog.songs.mapTo(mutableListOf(), MusicCatalog::mediaItem))
        } else {
            result.sendResult(mutableListOf())
        }
    }

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            mediaId ?: return
            MusicCatalog.songs.indexOfFirst { it.id == mediaId }
                .takeIf { it >= 0 }?.let {
                    currentIndex = it
                    updateCurrentSong()
                }
        }

        override fun onPlay() = updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)

        override fun onPause() = updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)

        override fun onSkipToNext() {
            currentIndex = (currentIndex + 1) % MusicCatalog.songs.size
            updateCurrentSong()
        }

        override fun onSkipToPrevious() {
            currentIndex = (currentIndex - 1 + MusicCatalog.songs.size) % MusicCatalog.songs.size
            updateCurrentSong()
        }
    }

    private fun updateCurrentSong() {
        mediaSession.setMetadata(MusicCatalog.metadata(MusicCatalog.songs[currentIndex]))
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
    }

    private fun updatePlaybackState(state: Int) {
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
                .build()
        )
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        const val MEDIA_ROOT = "media-root"
    }
}