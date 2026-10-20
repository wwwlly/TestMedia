package com.autoai.media.qqmusic.activity

import android.R
import android.content.ComponentName
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.autoai.media.qqmusic.service.MusicBrowserService
import com.autoai.media.qqmusic.databinding.ActivityMediaBinding

class MediaActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaBinding
    private lateinit var browser: MediaBrowserCompat

    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val controller = MediaControllerCompat(this@MediaActivity, browser.sessionToken)
            controller.registerCallback(controllerCallback)
            MediaControllerCompat.setMediaController(this@MediaActivity, controller)
            browser.subscribe(MusicBrowserService.Companion.MEDIA_ROOT, subscriptionCallback)
        }
    }

    private val subscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onError(parentId: String) {
            binding.songList.removeAllViews()
            binding.songList.addView(TextView(this@MediaActivity).apply {
                text = "歌曲列表加载失败，点击重试"
                setOnClickListener { retrySubscription(parentId) }
            })
        }

        override fun onChildrenLoaded(
            parentId: String,
            children: MutableList<MediaBrowserCompat.MediaItem>
        ) {
            binding.songList.removeAllViews()
            children.forEach { item ->
                val mediaId = item.mediaId ?: return@forEach
                val description = item.description
                val row = TextView(this@MediaActivity).apply {
                    text = "${description.title}\n${description.subtitle} · ${description.description}"
                    textSize = 17f
                    setTextColor(ContextCompat.getColor(context, R.color.black))
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(16, 18, 16, 18)
                    setOnClickListener { controls()?.playFromMediaId(mediaId, null) }
                }
                binding.songList.addView(row, ViewGroup.LayoutParams.MATCH_PARENT, 88)
            }
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            metadata ?: return
            binding.currentTitle.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            binding.currentArtist.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)?.let {
                binding.currentAlbumArt.setImageBitmap(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate")
        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.previousButton.setOnClickListener { controls()?.skipToPrevious() }
        binding.nextButton.setOnClickListener { controls()?.skipToNext() }
        browser = MediaBrowserCompat(this, componentName(), connectionCallback, null)
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart")
        browser.connect()
    }

    override fun onStop() {
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
        browser.disconnect()
        super.onStop()
        Log.i(TAG, "onStop")
    }

    private fun controls(): MediaControllerCompat.TransportControls? =
        MediaControllerCompat.getMediaController(this)?.transportControls

    private fun retrySubscription(parentId: String) {
        if (!browser.isConnected) return
        browser.unsubscribe(parentId)
        browser.subscribe(parentId, subscriptionCallback)
    }

    private fun componentName() = ComponentName(this, MusicBrowserService::class.java)

    override fun onRestart() {
        super.onRestart()
        Log.i(TAG, "onRestart")
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
    }

    companion object {
        private const val TAG = "MediaActivity"
    }
}
