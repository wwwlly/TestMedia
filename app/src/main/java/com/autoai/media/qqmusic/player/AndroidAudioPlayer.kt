package com.autoai.media.qqmusic.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper

/** Android MediaPlayer 适配器；每次 prepare 新建实例，隔离旧媒体源的异步回调。 */
class AndroidAudioPlayer(context: Context) : AudioPlayer {
    private val appContext = context.applicationContext
    private var engine: MediaPlayer? = null
    private var playWhenPrepared = false
    private var volume = 1f
    override var listener: AudioPlayer.Listener? = null
    override var state = PlaybackState.IDLE
        private set
    override var source: AudioSource? = null
        private set

    private val isPrepared: Boolean
        get() = state in setOf(PlaybackState.READY, PlaybackState.PLAYING,
            PlaybackState.PAUSED, PlaybackState.COMPLETED)

    override val durationMs: Long?
        get() { checkUsable(); return if (isPrepared) engine?.duration?.toLong() else null }
    override val positionMs: Long
        get() { checkUsable(); return if (isPrepared) engine?.currentPosition?.toLong() ?: 0 else 0 }

    override fun prepare(source: AudioSource, autoPlay: Boolean) {
        checkUsable()
        disposeEngine()
        this.source = source
        playWhenPrepared = autoPlay
        // Set up the engine before notifying listeners, which may issue another command.
        val player = MediaPlayer()
        engine = player
        player.setOnPreparedListener {
            if (engine !== it) return@setOnPreparedListener
            changeState(PlaybackState.READY)
            if (engine === it && state == PlaybackState.READY && playWhenPrepared) play()
        }
        player.setOnCompletionListener {
            if (engine === it) changeState(PlaybackState.COMPLETED)
        }
        player.setOnSeekCompleteListener {
            if (engine === it && isPrepared) listener?.onSeekCompleted(positionMs)
        }
        player.setOnInfoListener { current, what, _ ->
            if (engine === current) when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> listener?.onBufferingChanged(true)
                MediaPlayer.MEDIA_INFO_BUFFERING_END -> listener?.onBufferingChanged(false)
            }
            true
        }
        player.setOnErrorListener { current, what, extra ->
            if (engine === current) fail(PlayerError(PlayerError.Code.PLAYBACK,
                "MediaPlayer error: what=$what, extra=$extra", nativeCode = what, nativeExtra = extra))
            true // Consume the error to avoid MediaPlayer also reporting completion.
        }
        changeState(PlaybackState.PREPARING)
        if (engine !== player) return
        execute(PlayerError.Code.SOURCE) {
            player.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            player.setVolume(volume, volume)
            when (source) {
                is AudioSource.Uri -> player.setDataSource(appContext, Uri.parse(source.value), source.headers)
                is AudioSource.Asset -> appContext.assets.openFd(source.path).use {
                    player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            }
            player.prepareAsync()
        }
    }

    override fun play() {
        checkUsable()
        if (state == PlaybackState.PREPARING) { playWhenPrepared = true; return }
        check(isPrepared) { "Call prepare before play (state=$state)" }
        if (state == PlaybackState.PLAYING) return
        if (execute { engine!!.start() }) changeState(PlaybackState.PLAYING)
    }

    override fun pause() {
        checkUsable()
        playWhenPrepared = false
        if (state != PlaybackState.PLAYING) return
        if (execute { engine!!.pause() }) changeState(PlaybackState.PAUSED)
    }

    override fun stop() {
        checkUsable()
        disposeEngine()
        source = null
        playWhenPrepared = false
        changeState(PlaybackState.IDLE)
    }

    override fun seekTo(positionMs: Long) {
        checkUsable()
        require(positionMs >= 0)
        check(isPrepared) { "Cannot seek in $state" }
        val target = positionMs.coerceAtMost(durationMs ?: positionMs)
        execute { engine!!.seekTo(target, MediaPlayer.SEEK_CLOSEST) }
    }

    override fun setVolume(volume: Float) {
        checkUsable()
        require(volume.isFinite() && volume in 0f..1f)
        this.volume = volume
        if (engine != null) execute { engine!!.setVolume(volume, volume) }
    }

    override fun release() {
        checkMainThread()
        if (state == PlaybackState.RELEASED) return
        disposeEngine()
        source = null
        playWhenPrepared = false
        changeState(PlaybackState.RELEASED)
        listener = null
    }

    private fun disposeEngine() {
        val old = engine
        engine = null // Ignore callbacks already queued by the previous engine.
        old?.release()
    }

    private fun execute(code: PlayerError.Code = PlayerError.Code.PLAYBACK, block: () -> Unit): Boolean {
        return try { block(); true } catch (error: Exception) {
            fail(PlayerError(code, error.message ?: "MediaPlayer operation failed", error))
            false
        }
    }

    private fun fail(error: PlayerError) {
        disposeEngine()
        playWhenPrepared = false
        changeState(PlaybackState.ERROR)
        if (state == PlaybackState.ERROR) listener?.onError(error)
    }

    private fun changeState(value: PlaybackState) {
        if (state == value) return
        state = value
        listener?.onStateChanged(value)
    }

    private fun checkUsable() {
        checkMainThread()
        check(state != PlaybackState.RELEASED) { "Player has been released" }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Use AudioPlayer on the main thread" }
    }
}
