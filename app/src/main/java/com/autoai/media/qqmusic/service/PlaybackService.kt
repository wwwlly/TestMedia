package com.autoai.media.qqmusic.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.autoai.media.qqmusic.activity.MediaActivity

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackService", "Playback failed: " + error.errorCodeName + ", uri=" + SAMPLE_URL, error)
                player.pause()
            }
        })
        player.setMediaItem(
            MediaItem.Builder().setMediaId("sample-mp3").setUri(SAMPLE_URL)
                .setMediaMetadata(MediaMetadata.Builder().setTitle("sample.mp3")
                    .setArtist("Sample Files").build()).build()
        )
        val sessionActivity = PendingIntent.getActivity(
            this, 0, Intent(this, MediaActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep active playback running when the user dismisses the app from recents.
        if (mediaSession?.player?.playWhenReady != true) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val SAMPLE_URL = "https://www.samplefiles.online/files/sample.mp3"
    }
}
