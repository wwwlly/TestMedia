package com.autoai.media.qqmusic.catalog

import android.content.Context

/** 数据源唯一装配入口，可与 AudioPlayerFactory 分别切换。每次创建独立实例。 */
object MediaDataSourceFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context): MediaDataSource = MusicCatalog()
}
