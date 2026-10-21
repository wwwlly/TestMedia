package com.autoai.media.qqmusic.catalog

import android.content.Context
import com.autoai.media.qqmusic.R
import com.autoai.media.qqmusic.player.AudioSource

/** 本地演示数据源；四条展示数据共用测试音频，并非对应歌曲的真实录音。 */
class MusicCatalog(context: Context) : MediaDataSource {
    private val appContext = context.applicationContext
    private var released = false
    private val songs = listOf(
        Song(
            "sunny-day",
            "晴天",
            "周杰伦",
            "叶惠美",
            0xFF5C6BC0.toInt()
        ),
        Song(
            "mild-summer",
            "夏天的风",
            "温岚",
            "蓝色雨",
            0xFF26A69A.toInt()
        ),
        Song(
            "travel",
            "平凡之路",
            "朴树",
            "猎户星座",
            0xFFFF8A65.toInt()
        ),
        Song(
            "youth",
            "岁月神偷",
            "金玟岐",
            "完美世界",
            0xFFAB47BC.toInt()
        )
    )

    override fun loadSongs(callback: (Result<List<Song>>) -> Unit): MediaRequest {
        check(!released)
        callback(Result.success(songs))
        return MediaRequest.COMPLETED
    }

    override fun resolveSource(mediaId: String, callback: (Result<AudioSource>) -> Unit): MediaRequest {
        check(!released)
        callback(if (songs.any { it.id == mediaId }) Result.success(AudioSource.Asset("music/test.mp3"))
            else Result.failure(IllegalArgumentException(appContext.getString(R.string.error_unknown_media_id, mediaId))))
        return MediaRequest.COMPLETED
    }

    override fun release() { released = true }
}
