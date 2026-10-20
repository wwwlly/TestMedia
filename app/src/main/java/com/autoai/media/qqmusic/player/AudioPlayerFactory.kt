package com.autoai.media.qqmusic.player

import android.content.Context

/** 播放引擎的唯一装配入口；更换 SDK 时只需替换这里的实现。每次创建独立实例。 */
object AudioPlayerFactory {
    fun create(context: Context): AudioPlayer = AndroidAudioPlayer(context)
}
