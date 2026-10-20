package com.autoai.media.qqmusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.autoai.media.qqmusic.activity.MediaActivity
import com.autoai.media.qqmusic.catalog.MediaCatalogMapper
import com.autoai.media.qqmusic.catalog.MediaDataSource
import com.autoai.media.qqmusic.catalog.MediaDataSourceFactory
import com.autoai.media.qqmusic.catalog.MediaRequest
import com.autoai.media.qqmusic.catalog.Song
import com.autoai.media.qqmusic.player.AudioPlayer
import com.autoai.media.qqmusic.player.AudioPlayerFactory
import com.autoai.media.qqmusic.player.PlaybackState
import com.autoai.media.qqmusic.player.PlayerError

class MusicBrowserService : MediaBrowserServiceCompat() {
    private lateinit var mediaSession: MediaSessionCompat
    // 服务不引用任何具体播放器，实现选择统一放在 AudioPlayerFactory。
    private lateinit var player: AudioPlayer
    private lateinit var dataSource: MediaDataSource
    private var songs: List<Song> = emptyList()
    private var catalogReady = false
    private var loadingCatalog = false
    private var destroyed = false
    private var catalogRequest: MediaRequest? = null
    private var sourceRequest: MediaRequest? = null
    private var sourceGeneration = 0
    private var resolvingSource = false
    private var playAfterLoad = false
    private var requestedMediaId: String? = null
    private var changingSong = false
    private val pendingBrowsers = mutableListOf<Result<MutableList<MediaBrowserCompat.MediaItem>>>()
    private var currentIndex = 0
    private var buffering = false
    private var foreground = false
    private var errorMessage: String? = null
    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest
    private var hasAudioFocus = false
    private var resumeOnFocusGain = false
    private var wantsPlayback = false

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "$packageName:TestMediaMusicSession").apply {
            setCallback(sessionCallback)
            isActive = true
        }
        sessionToken = mediaSession.sessionToken
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "音乐播放", NotificationManager.IMPORTANCE_LOW)
        )
        player = AudioPlayerFactory.create(this)
        dataSource = MediaDataSourceFactory.create(this)
        audioManager = getSystemService(AudioManager::class.java)
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                Log.i(TAG, "Audio focus changed: $change")
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        hasAudioFocus = true
                        if (resumeOnFocusGain && wantsPlayback) {
                            resumeOnFocusGain = false
                            sessionCallback.onPlay()
                        }
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        hasAudioFocus = false
                        resumeOnFocusGain = wantsPlayback
                        player.pause()
                    }
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        player.pause()
                        abandonFocus()
                    }
                }
            }, Handler(Looper.getMainLooper()))
            .build()
        player.listener = object : AudioPlayer.Listener {
            override fun onStateChanged(state: PlaybackState) {
                // Replacing an engine source is not a user stop; retain the started foreground service.
                if (changingSong && state == PlaybackState.IDLE) return
                if (state != PlaybackState.PLAYING) buffering = false
                if (state == PlaybackState.READY) updateMetadata()
                publishState()
                when (state) {
                    PlaybackState.IDLE, PlaybackState.ERROR, PlaybackState.COMPLETED -> {
                        abandonFocus()
                        endForeground()
                    }
                    else -> if (foreground) startForeground(NOTIFICATION_ID, notification())
                }
            }

            override fun onBufferingChanged(isBuffering: Boolean) {
                buffering = isBuffering
                publishState()
            }

            override fun onSeekCompleted(positionMs: Long) = publishState()

            override fun onError(error: PlayerError) {
                Log.e(TAG, error.message, error.cause)
                errorMessage = error.message
                publishState()
            }
        }
        publishState()
        loadCatalog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_KEEP_PLAYING) {
            // Promote again after the framework delivers this foreground-service start request.
            // A promotion made earlier in a session callback may precede its delivery.
            startForeground(NOTIFICATION_ID, notification())
            if (!foreground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        } else {
            MediaButtonReceiver.handleIntent(mediaSession, intent)
        }
        return START_NOT_STICKY
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot(MEDIA_ROOT, null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        if (parentId != MEDIA_ROOT) {
            result.sendResult(mutableListOf())
        } else if (catalogReady) {
            result.sendResult(songs.mapTo(mutableListOf(), MediaCatalogMapper::mediaItem))
        } else {
            result.detach()
            pendingBrowsers.add(result)
            loadCatalog()
        }
    }

    private fun loadCatalog() {
        if (loadingCatalog || destroyed) return
        loadingCatalog = true
        catalogRequest = dataSource.loadSongs { outcome ->
            if (destroyed) return@loadSongs
            loadingCatalog = false
            val loaded = outcome.getOrNull()
            val valid = loaded != null && loaded.all { it.id.isNotBlank() } &&
                loaded.map { it.id }.distinct().size == loaded.size
            if (valid) {
                songs = loaded!!.toList()
                catalogReady = true
                currentIndex = 0
                updateMetadata(includeDuration = false)
            }
            val waiting = pendingBrowsers.toList()
            pendingBrowsers.clear()
            waiting.forEach { it.sendResult(if (valid)
                songs.mapTo(mutableListOf(), MediaCatalogMapper::mediaItem) else null) }
            if (!valid) {
                reportDataError(outcome.exceptionOrNull()?.message ?: "歌曲列表包含空 ID 或重复 ID")
            } else if (playAfterLoad) {
                playAfterLoad = false
                val id = requestedMediaId
                requestedMediaId = null
                val index = if (id == null) 0 else songs.indexOfFirst { it.id == id }
                selectSong(index)
            }
        }
    }

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            mediaId ?: return
            if (!catalogReady) {
                requestedMediaId = mediaId
                playAfterLoad = true
                if (beginForeground()) {
                    publishState()
                    loadCatalog()
                } else playAfterLoad = false
            } else selectSong(songs.indexOfFirst { it.id == mediaId })
        }

        override fun onPlay() {
            if (!beginForeground()) return
            if (!catalogReady) {
                playAfterLoad = true
                requestedMediaId = null
                publishState()
                loadCatalog()
                return
            }
            if (songs.isEmpty()) { reportDataError("暂无可播放歌曲"); return }
            if (!requestFocus()) return
            wantsPlayback = true
            errorMessage = null
            when (player.state) {
                PlaybackState.IDLE, PlaybackState.ERROR -> prepareCurrentSong()
                else -> player.play()
            }
        }

        override fun onPause() {
            playAfterLoad = false
            cancelSourceRequest()
            wantsPlayback = false
            resumeOnFocusGain = false
            player.pause()
            publishState() // Also handles cancellation of autoPlay during preparation.
            if (foreground) startForeground(NOTIFICATION_ID, notification())
        }

        override fun onStop() {
            playAfterLoad = false
            cancelSourceRequest()
            player.stop()
            endForeground()
        }

        override fun onSeekTo(pos: Long) {
            if (pos >= 0 && player.state in setOf(PlaybackState.READY, PlaybackState.PLAYING,
                    PlaybackState.PAUSED, PlaybackState.COMPLETED)) player.seekTo(pos)
        }

        override fun onSkipToNext() {
            if (songs.isNotEmpty()) selectSong((currentIndex + 1) % songs.size)
        }
        override fun onSkipToPrevious() {
            if (songs.isNotEmpty()) selectSong((currentIndex - 1 + songs.size) % songs.size)
        }
    }

    private fun selectSong(index: Int) {
        if (index !in songs.indices) { reportDataError("找不到可播放歌曲"); return }
        cancelSourceRequest()
        changingSong = true
        try { player.stop() } finally { changingSong = false }
        currentIndex = index
        errorMessage = null
        updateMetadata(includeDuration = false)
        if (beginForeground() && requestFocus()) {
            wantsPlayback = true
            prepareCurrentSong()
        }
    }

    private fun requestFocus(): Boolean {
        if (hasAudioFocus) return true
        val result = audioManager.requestAudioFocus(focusRequest)
        Log.i(TAG, "Audio focus request result=$result")
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasAudioFocus) {
            player.pause()
            abandonFocus()
            mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_ERROR, player.positionMs, 0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY)
                .setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, "未获得音频焦点，请稍后重试").build())
            endForeground()
        }
        return hasAudioFocus
    }

    private fun abandonFocus() {
        wantsPlayback = false
        resumeOnFocusGain = false
        hasAudioFocus = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun prepareCurrentSong() {
        val song = songs.getOrNull(currentIndex) ?: return
        cancelSourceRequest()
        buffering = false
        resolvingSource = true
        publishState()
        val generation = sourceGeneration
        sourceRequest = dataSource.resolveSource(song.id) { outcome ->
            // Even SDK callbacks queued before cancel must not restart a previous track.
            if (destroyed || generation != sourceGeneration) return@resolveSource
            resolvingSource = false
            outcome.fold(
                onSuccess = { player.prepare(it, autoPlay = wantsPlayback && hasAudioFocus) },
                onFailure = { reportDataError(it.message ?: "无法获取播放地址") }
            )
        }
    }

    private fun cancelSourceRequest() {
        sourceGeneration++
        resolvingSource = false
        sourceRequest?.cancel()
        sourceRequest = null
    }

    private fun reportDataError(message: String) {
        Log.e(TAG, "Media data error: $message")
        playAfterLoad = false
        requestedMediaId = null
        cancelSourceRequest()
        player.pause()
        abandonFocus()
        endForeground()
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_ERROR, player.positionMs, 0f)
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
            .setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, message).build())
    }

    private fun updateMetadata(includeDuration: Boolean = true) {
        val song = songs.getOrNull(currentIndex) ?: return
        val metadata = MediaCatalogMapper.metadata(song)
        // Old engine duration must not leak into metadata while changing tracks.
        val duration = if (includeDuration) player.durationMs else null
        mediaSession.setMetadata(MediaMetadataCompat.Builder(metadata)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration ?: -1L).build())
    }

    private fun publishState() {
        val state = if (resolvingSource || playAfterLoad) PlaybackStateCompat.STATE_BUFFERING else when (player.state) {
            PlaybackState.IDLE -> PlaybackStateCompat.STATE_STOPPED
            PlaybackState.PREPARING -> PlaybackStateCompat.STATE_BUFFERING
            PlaybackState.READY, PlaybackState.PAUSED -> PlaybackStateCompat.STATE_PAUSED
            PlaybackState.PLAYING -> if (buffering) PlaybackStateCompat.STATE_BUFFERING else PlaybackStateCompat.STATE_PLAYING
            PlaybackState.COMPLETED -> PlaybackStateCompat.STATE_STOPPED
            PlaybackState.ERROR -> PlaybackStateCompat.STATE_ERROR
            PlaybackState.RELEASED -> PlaybackStateCompat.STATE_NONE
        }
        val builder = PlaybackStateCompat.Builder().setActions(
            PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
        ).setState(state, player.positionMs, if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f)
        if (state == PlaybackStateCompat.STATE_ERROR) builder.setErrorMessage(
            PlaybackStateCompat.ERROR_CODE_APP_ERROR, errorMessage ?: "播放失败，请重试")
        mediaSession.setPlaybackState(builder.build())
    }

    private fun beginForeground(): Boolean {
        return try {
            if (!foreground) ContextCompat.startForegroundService(this,
                Intent(this, MusicBrowserService::class.java).setAction(ACTION_KEEP_PLAYING))
            startForeground(NOTIFICATION_ID, notification())
            foreground = true
            true
        } catch (error: RuntimeException) {
            Log.e(TAG, "Cannot start playback service", error)
            player.pause()
            mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_ERROR, player.positionMs, 0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY)
                .setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, "无法启动后台播放，请在应用内重试").build())
            false
        }
    }

    private fun notification(): Notification {
        val contentIntent = PendingIntent.getActivity(this, 0, Intent(this, MediaActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val song = songs.getOrNull(currentIndex)
        val playing = wantsPlayback || player.state == PlaybackState.PLAYING || player.state == PlaybackState.PREPARING
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song?.title ?: "正在加载歌曲").setContentText(song?.artist)
            .setContentIntent(contentIntent).setOnlyAlertOnce(true)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "上一曲", mediaAction(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "暂停" else "播放",
                mediaAction(if (playing) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY))
            .addAction(android.R.drawable.ic_media_next, "下一曲", mediaAction(PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", mediaAction(PlaybackStateCompat.ACTION_STOP))
            .build()
    }

    private fun mediaAction(action: Long): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setClass(this, MusicBrowserService::class.java)
            .putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
                PlaybackStateCompat.toKeyCode(action)))
        return PendingIntent.getService(this, action.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun endForeground() {
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    override fun onDestroy() {
        destroyed = true
        catalogRequest?.cancel()
        cancelSourceRequest()
        dataSource.release()
        pendingBrowsers.forEach { it.sendResult(null) }
        pendingBrowsers.clear()
        abandonFocus()
        player.listener = null
        player.release()
        mediaSession.release()
        endForeground()
        super.onDestroy()
    }

    companion object {
        const val MEDIA_ROOT = "media-root"
        private const val TAG = "MusicBrowserService"
        private const val CHANNEL_ID = "music-playback"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_KEEP_PLAYING = "com.autoai.media.qqmusic.KEEP_PLAYING"
    }
}
