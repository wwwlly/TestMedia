package com.autoai.media.qqmusic.activity

import com.autoai.media.qqmusic.R
import com.autoai.media.qqmusic.media.session.MediaSessionConnection

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.autoai.media.qqmusic.databinding.ActivityPlaybackBinding
import java.util.Locale

class PlaybackActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlaybackBinding
    private lateinit var connection: MediaSessionConnection
    private val handler = Handler(Looper.getMainLooper())
    private var pendingMediaId: String? = null
    private var dragging = false
    private val progressUpdater = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pendingMediaId = if (savedInstanceState == null) intent.getStringExtra(EXTRA_MEDIA_ID)
            else savedInstanceState.getString(STATE_PENDING_ID)
        connection = MediaSessionConnection(this, {
            // Consume once: reconnection or rotation must not restart the selected song.
            pendingMediaId?.let { id ->
                pendingMediaId = null
                connection.playFromMediaId(id)
            }
        }, ::render)
        binding.backButton.setOnClickListener { returnToList() }
        binding.previousButton.setOnClickListener { connection.skipToPrevious() }
        binding.nextButton.setOnClickListener { connection.skipToNext() }
        binding.stopButton.setOnClickListener { connection.stopPlayback() }
        binding.playPauseButton.setOnClickListener {
            if (connection.isConnected) {
                val state = connection.playbackState?.state
                if (state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_BUFFERING)
                    connection.pause() else connection.play()
            }
        }
        binding.progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) { dragging = true }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.elapsedTime.text = formatTime(duration() * progress / 1000)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                connection.seekTo(duration() * seekBar.progress / 1000)
                dragging = false
            }
        })
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingMediaId = intent.getStringExtra(EXTRA_MEDIA_ID)
        if (connection.isConnected) {
            pendingMediaId?.let { id ->
                pendingMediaId = null
                connection.playFromMediaId(id)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PENDING_ID, pendingMediaId)
        super.onSaveInstanceState(outState)
    }

    private fun returnToList() {
        if (isTaskRoot) startActivity(Intent(this, MediaActivity::class.java))
        finish()
    }

    override fun onStart() {
        super.onStart()
        connection.start()
        handler.post(progressUpdater)
    }
    override fun onStop() {
        handler.removeCallbacks(progressUpdater)
        dragging = false
        connection.stop()
        super.onStop()
    }

    private fun render() {
        val metadata = connection.metadata
        binding.currentTitle.text = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: getString(R.string.song_info_waiting)
        binding.currentArtist.text = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST).orEmpty()
        binding.currentAlbum.text = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM).orEmpty()
        binding.currentAlbumArt.setImageBitmap(metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
        val state = connection.playbackState
        val active = state?.state == PlaybackStateCompat.STATE_PLAYING || state?.state == PlaybackStateCompat.STATE_BUFFERING
        binding.playPauseButton.text = when {
            state?.state == PlaybackStateCompat.STATE_ERROR -> getString(R.string.action_retry)
            active -> getString(R.string.action_pause)
            else -> getString(R.string.action_play)
        }
        binding.playbackStatus.text = when {
            !connection.isConnected -> getString(R.string.media_connection_return_retry)
            state?.state == PlaybackStateCompat.STATE_ERROR -> state.errorMessage ?: getString(R.string.playback_failed_retry)
            state?.state == PlaybackStateCompat.STATE_BUFFERING -> getString(R.string.playback_loading)
            state?.state == PlaybackStateCompat.STATE_PLAYING -> getString(R.string.playback_playing)
            state?.state == PlaybackStateCompat.STATE_PAUSED -> getString(R.string.playback_paused)
            state?.state == PlaybackStateCompat.STATE_STOPPED -> getString(R.string.playback_stopped)
            else -> getString(R.string.playback_tap_to_play)
        }
        fun supports(action: Long) = connection.isConnected && ((state?.actions ?: 0L) and action) != 0L
        binding.previousButton.isEnabled = supports(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        binding.nextButton.isEnabled = supports(PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        binding.stopButton.isEnabled = supports(PlaybackStateCompat.ACTION_STOP)
        binding.playPauseButton.isEnabled = supports(if (active) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY)
        binding.progress.isEnabled = supports(PlaybackStateCompat.ACTION_SEEK_TO) && duration() > 0 &&
            state?.state in listOf(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.STATE_PAUSED)
        updateProgress()
    }

    private fun duration() = (connection.metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)

    private fun updateProgress() {
        val total = duration()
        binding.totalTime.text = formatTime(total)
        if (dragging) return
        val state = connection.playbackState
        var position = (state?.position ?: 0L).coerceAtLeast(0L)
        // PlaybackState positions are snapshots; extrapolate only during actual playback.
        if (state?.state == PlaybackStateCompat.STATE_PLAYING && state.lastPositionUpdateTime > 0) {
            position += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
        if (total > 0) position = position.coerceAtMost(total)
        binding.elapsedTime.text = formatTime(position)
        binding.progress.progress = if (total > 0) (position * 1000 / total).toInt() else 0
    }

    private fun formatTime(ms: Long): String = String.format(Locale.ROOT, getString(R.string.playback_time_format), ms / 60_000, ms / 1000 % 60)

    companion object {
        private const val EXTRA_MEDIA_ID = "media_id"
        private const val STATE_PENDING_ID = "pending_media_id"
        fun intent(context: Context, mediaId: String? = null) = Intent(context, PlaybackActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (mediaId != null) putExtra(EXTRA_MEDIA_ID, mediaId) }
    }
}
