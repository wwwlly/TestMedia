package com.autoai.media.qqmusic.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.annotation.StyleRes
import com.autoai.media.qqmusic.R
import com.autoai.media.qqmusic.databinding.DialogTestBinding

class TestDialog(
    context: Context,
    @StyleRes themeResId: Int = R.style.common_dialog
) : Dialog(context, themeResId) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = DialogTestBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        binding.confirmButton.setOnClickListener { dismiss() }
        binding.cancelButton.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()

        window?.let { window ->
            window.setGravity(Gravity.CENTER)

            val params = window.attributes
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            window.attributes = params
        }
    }
}
