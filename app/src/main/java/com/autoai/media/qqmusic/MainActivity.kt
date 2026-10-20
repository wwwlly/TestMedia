package com.autoai.media.qqmusic

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.autoai.media.qqmusic.activity.MediaActivity
import com.autoai.media.qqmusic.databinding.ActivityMainBinding
import com.autoai.media.qqmusic.dialog.TestDialogFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.showCustomAnimation.setOnClickListener { showDialog(TestDialogFragment.WindowAnimationMode.CUSTOM) }
        binding.showSystemAnimation.setOnClickListener { showDialog(TestDialogFragment.WindowAnimationMode.SYSTEM_DEFAULT) }
        binding.showNoAnimation.setOnClickListener { showDialog(TestDialogFragment.WindowAnimationMode.NONE) }
        binding.openMedia.setOnClickListener {
            startActivity(Intent(this, MediaActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart")
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop")
    }

    private fun showDialog(mode: TestDialogFragment.WindowAnimationMode) {
        TestDialogFragment.newInstance(mode)
            .show(supportFragmentManager, "dialog-${mode.name.lowercase()}")
    }

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
        private const val TAG = "MainActivity"
    }
}
