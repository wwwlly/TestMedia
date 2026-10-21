package com.autoai.media.qqmusic.activity

import com.autoai.media.qqmusic.R
import com.autoai.media.qqmusic.adapter.SongAdapter

import com.autoai.media.qqmusic.media.session.MediaSessionConnection

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.autoai.media.qqmusic.databinding.ActivityMediaBinding
import com.autoai.media.qqmusic.service.MusicBrowserService

/** 歌曲列表页，数据只来自 MediaBrowser，不依赖具体数据源。 */
class MediaActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaBinding
    private lateinit var connection: MediaSessionConnection
    private val songs = SongAdapter { id ->
        val controller = connection.controller
        val currentId = controller?.metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
        val canReturnToPlayback = id == currentId && controller?.playbackState?.state in listOf(
            PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.STATE_PAUSED,
            PlaybackStateCompat.STATE_BUFFERING, PlaybackStateCompat.STATE_CONNECTING
        )
        startActivity(PlaybackActivity.intent(this, if (canReturnToPlayback) null else id))
    }
    private val subscription = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(
            parentId: String,
            children: MutableList<MediaBrowserCompat.MediaItem>
        ) {
            songs.submitList(children.filter { it.isPlayable && it.mediaId != null })
            binding.listStatus.text = if (children.isEmpty()) getString(R.string.songs_empty) else ""
            binding.listStatus.visibility = if (children.isEmpty()) View.VISIBLE else View.GONE
            binding.retryButton.visibility = View.GONE
        }

        override fun onError(parentId: String) {
            showError(getString(R.string.songs_load_failed))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.songList.layoutManager = LinearLayoutManager(this)
        binding.songList.adapter = songs
        connection = MediaSessionConnection(this, { subscribe() }, ::updateCurrentSong)
        binding.retryButton.setOnClickListener {
            if (connection.browser.isConnected) subscribe() else {
                connection.stop()
                connection.start()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.listStatus.text = getString(R.string.songs_loading)
        binding.listStatus.visibility = View.VISIBLE
        binding.retryButton.visibility = View.GONE
        connection.start()
    }

    override fun onStop() {
        connection.stop()
        super.onStop()
    }

    private fun subscribe() {
        binding.listStatus.text = getString(R.string.songs_loading)
        binding.retryButton.visibility = View.GONE
        connection.browser.unsubscribe(MusicBrowserService.MEDIA_ROOT)
        connection.browser.subscribe(MusicBrowserService.MEDIA_ROOT, subscription)
    }

    private fun updateCurrentSong(controller: MediaControllerCompat?) {
        val mediaId = controller?.metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
        val status = when (controller?.playbackState?.state) {
            PlaybackStateCompat.STATE_PLAYING -> getString(R.string.song_status_playing)
            PlaybackStateCompat.STATE_PAUSED -> getString(R.string.playback_paused)
            PlaybackStateCompat.STATE_BUFFERING, PlaybackStateCompat.STATE_CONNECTING -> getString(R.string.song_status_loading)
            PlaybackStateCompat.STATE_STOPPED -> getString(R.string.playback_stopped)
            PlaybackStateCompat.STATE_ERROR -> getString(R.string.song_status_failed)
            else -> null
        }
        songs.updatePlayback(mediaId, status)
        if (controller == null) showError(getString(R.string.media_connection_retry))
    }

    private fun showError(message: String) {
        binding.listStatus.text = message
        binding.listStatus.visibility = View.VISIBLE
        binding.retryButton.visibility = View.VISIBLE
    }
}
