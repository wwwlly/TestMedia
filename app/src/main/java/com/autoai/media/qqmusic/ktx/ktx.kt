package com.autoai.media.qqmusic.ktx

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import com.autoai.media.qqmusic.catalog.Song

/** 将歌曲已有信息映射到媒体元数据，缺失的信息使用空字符串、0 或 null。 */
fun Song.toMediaMetadataCompat(): MediaMetadataCompat = MediaMetadataCompat.Builder()
    // 媒体唯一标识，用于定位和播放歌曲。
    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id)
    // 歌曲标题。
    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
    // 展示标题，使用歌曲标题。
    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
    // 歌曲演唱者。
    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
    // 歌曲所属专辑名称。
    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
    // 展示副标题，使用演唱者名称。
    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, artist)
    // 专辑艺术家：Song 未提供，不使用歌曲演唱者代替，默认空字符串。
    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, "")
    // 专辑封面地址，未提供时默认空字符串。
    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artworkUri.orEmpty())
    // 作曲者：Song 未提供，默认空字符串。
    .putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, "")
    // 独立歌曲配图地址：Song 未提供，默认空字符串。
    .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, "")
    // 歌曲时长，单位为毫秒；Song 未提供，默认 0。
    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0L)
    // 发行日期：Song 未提供，默认空字符串。
    .putString(MediaMetadataCompat.METADATA_KEY_DATE, "")
    // 音乐流派：Song 未提供，默认空字符串。
    .putString(MediaMetadataCompat.METADATA_KEY_GENRE, "")
    // 展示描述，使用专辑名称。
    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, album)
    // 展示图标地址，使用封面地址，未提供时默认空字符串。
    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artworkUri.orEmpty())
    // 专辑曲目总数：Song 未提供，默认 0；该字段不是播放次数。
    .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, 0L)
    // 媒体评分：Song 未提供，默认未评分，使用心形评分类型。
    .putRating(MediaMetadataCompat.METADATA_KEY_RATING, RatingCompat.newUnratedRating(RatingCompat.RATING_HEART))
    // 自定义 VIP 图标：Song 未提供，默认 null。
    .putBitmap("METADATA_KEY_VIP_ICON", null)
    .build()
