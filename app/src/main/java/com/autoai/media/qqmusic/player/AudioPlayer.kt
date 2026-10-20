package com.autoai.media.qqmusic.player

/**
 * 与播放引擎无关的单音频播放器。所有方法、属性和回调均在主线程使用。
 * 不负责播放列表、音频焦点、通知栏或后台服务，由上层业务统一管理。
 */
interface AudioPlayer {
    val state: PlaybackState
    val source: AudioSource?
    /** 未准备完成时返回 null；单位均为毫秒。 */
    val durationMs: Long?
    val positionMs: Long
    var listener: Listener?

    /** 替换媒体源并异步准备；可在任意未释放状态调用，也用于错误后重试。 */
    fun prepare(source: AudioSource, autoPlay: Boolean = false)

    /** 准备期间记录播放意图；完成后重播；IDLE/ERROR 状态必须先 prepare。 */
    fun play()
    /** 准备期间取消自动播放；其他非播放状态不操作。 */
    fun pause()
    /** 取消准备/播放并回到 IDLE，清空媒体源，之后需重新 prepare。 */
    fun stop()
    /** 仅 READY/PLAYING/PAUSED/COMPLETED 可用；完成结果通过 onSeekCompleted 通知。 */
    fun seekTo(positionMs: Long)
    /** 音量范围 0..1，跨媒体源保留。 */
    fun setVolume(volume: Float)
    /** 可重复调用。释放后除读取 state/source 外，不可继续使用。 */
    fun release()

    interface Listener {
        fun onStateChanged(state: PlaybackState) {}
        fun onBufferingChanged(isBuffering: Boolean) {}
        fun onSeekCompleted(positionMs: Long) {}
        fun onError(error: PlayerError) {}
    }
}

enum class PlaybackState { IDLE, PREPARING, READY, PLAYING, PAUSED, COMPLETED, ERROR, RELEASED }

/** 不向调用方暴露 Android MediaPlayer 或第三方 SDK 的数据类型。 */
sealed class AudioSource {
    /** HTTP(S)、file://、content:// 等引擎支持的 URI。网络请求需由宿主声明 INTERNET。 */
    data class Uri(val value: String, val headers: Map<String, String> = emptyMap()) : AudioSource() {
        init { require(value.isNotBlank()) }
    }

    /** 相对于 assets 的路径，例如 music/test.mp3。 */
    data class Asset(val path: String) : AudioSource() {
        init { require(path.isNotBlank() && !path.startsWith('/')) }
    }
}

/** code 为封装层分类，nativeCode/nativeExtra 保留引擎错误用于排查。 */
data class PlayerError(
    val code: Code,
    val message: String,
    val cause: Throwable? = null,
    val nativeCode: Int? = null,
    val nativeExtra: Int? = null
) {
    enum class Code { SOURCE, PLAYBACK }
}
