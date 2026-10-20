package com.autoai.media.qqmusic.catalog

import com.autoai.media.qqmusic.player.AudioSource

/**
 * 媒体数据提供方，不暴露 SDK 类型。方法及回调均在主线程使用。
 * 耗时工作由实现放到后台；每个请求回调一次（允许同步回调），取消后不再回调。
 */
interface MediaDataSource {
    fun loadSongs(callback: (Result<List<Song>>) -> Unit): MediaRequest
    /** 播放前获取媒体源，SDK 可在这里刷新临时 URL、请求头或完成鉴权。 */
    fun resolveSource(mediaId: String, callback: (Result<AudioSource>) -> Unit): MediaRequest
    /** 取消所有请求并释放 SDK 监听器等资源，允许重复调用。 */
    fun release()
}

fun interface MediaRequest {
    fun cancel()
    companion object { val COMPLETED = MediaRequest { } }
}

/** 稳定 ID 用于浏览和播放；不包含 SDK 对象和易过期的播放地址。 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val color: Int = 0xFF5C6BC0.toInt(),
    val artworkUri: String? = null
)
