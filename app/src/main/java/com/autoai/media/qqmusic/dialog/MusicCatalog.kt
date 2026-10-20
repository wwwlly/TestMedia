package com.autoai.media.qqmusic.dialog

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val color: Int
)

object MusicCatalog {
    val songs = listOf(
        Song("sunny-day", "晴天", "周杰伦", "叶惠美", 0xFF5C6BC0.toInt()),
        Song("mild-summer", "夏天的风", "温岚", "蓝色雨", 0xFF26A69A.toInt()),
        Song("travel", "平凡之路", "朴树", "猎户星座", 0xFFFF8A65.toInt()),
        Song("youth", "岁月神偷", "金玟岐", "完美世界", 0xFFAB47BC.toInt())
    )

    fun find(id: String): Song? = songs.firstOrNull { it.id == id }

    fun albumArt(song: Song): Bitmap {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(song.color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55FFFFFF
            textSize = 72f
            textAlign = Paint.Align.CENTER
        }
        Canvas(bitmap).drawText(song.title.take(1), 128f, 155f, paint)
        return bitmap
    }

    fun metadata(song: Song): MediaMetadataCompat = MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, song.id)
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt(song))
        .build()

    fun mediaItem(song: Song): MediaBrowserCompat.MediaItem =
        MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(song.id)
                .setTitle(song.title)
                .setSubtitle(song.artist)
                .setDescription(song.album)
                .setIconBitmap(albumArt(song))
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
}
