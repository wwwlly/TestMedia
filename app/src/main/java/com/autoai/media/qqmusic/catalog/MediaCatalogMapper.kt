package com.autoai.media.qqmusic.catalog

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat

/** 统一模型到系统媒体协议的转换，与数据来源无关。 */
object MediaCatalogMapper {
    private fun albumArt(song: Song): Bitmap {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(song.color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55FFFFFF
            textSize = 72f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(song.title.take(1), 128f, 155f, paint)
        return bitmap
    }

    fun metadata(song: Song): MediaMetadataCompat = MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, song.id)
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, song.artworkUri)
        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt(song))
        .build()

    fun mediaItem(song: Song): MediaBrowserCompat.MediaItem = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder().setMediaId(song.id).setTitle(song.title)
            .setSubtitle(song.artist).setDescription(song.album)
            .setIconUri(song.artworkUri?.let(Uri::parse)).setIconBitmap(albumArt(song)).build(),
        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
    )
}
