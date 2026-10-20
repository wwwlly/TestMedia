package com.autoai.media.qqmusic.dialog

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.annotation.StyleRes
import androidx.fragment.app.DialogFragment
import com.autoai.media.qqmusic.R
import com.autoai.media.qqmusic.databinding.DialogTestBinding
import android.animation.Animator
import android.animation.AnimatorListenerAdapter

class TestDialogFragment : DialogFragment() {

    private var _binding: DialogTestBinding? = null
    private val binding: DialogTestBinding
        get() = _binding!!

    private val animationMode: WindowAnimationMode
        get() = arguments
            ?.getString(ARG_ANIMATION_MODE)
            ?.let(WindowAnimationMode::valueOf)
            ?: WindowAnimationMode.SYSTEM_DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, animationMode.dialogTheme)
    }

//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
////        dialog?.window?.setWindowAnimations(0)
//        return super.onCreateView(inflater, container, savedInstanceState)
//    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogTestBinding.inflate(
            LayoutInflater.from(requireContext())
        )
        val dialog = Dialog(requireContext())
        dialog.setContentView(binding.root)

        binding.confirmButton.setOnClickListener { dismiss() }
        binding.cancelButton.setOnClickListener { dismiss() }

        return dialog
    }


    override fun dismiss() {
        super.dismiss()
//        playDismissAnimation(binding.root) {
//            super.dismiss()
//        }
    }

    // 用户主动关闭 Dialog
    private fun dismissWithAnimation() {
        playDismissAnimation(binding.root) {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        requireDialog().window ?: return

        // 解决快速 Home 时 Window 动画异常
//        dialog?.window?.setWindowAnimations(0)
//
//        playEnterAnimation(binding.root)

        dialog?.window?.let { dialogWindow ->
            // 1. 显式设置居中
            dialogWindow.setGravity(Gravity.CENTER)

            val params = dialogWindow.attributes
            // 2. 显式设置LayoutParams尺寸（避免未测量时默认为0/左上角）val params = window.attributes
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            dialogWindow.attributes = params

            Log.i(
                TAG,
                "mode=$animationMode, window.attributes.windowAnimations=" +
                        dialogWindow.attributes.windowAnimations
            )
        }
//        window.setGravity(Gravity.CENTER)
//        val params = window.attributes
//        params.width = WindowManager.LayoutParams.WRAP_CONTENT
//        params.height = WindowManager.LayoutParams.WRAP_CONTENT
//        window.attributes = params


    }


    enum class WindowAnimationMode(@StyleRes val dialogTheme: Int) {
        CUSTOM(R.style.common_dialog),
        SYSTEM_DEFAULT(R.style.Theme_TestMedia_Dialog_SystemDefault),
        NONE(R.style.Theme_TestMedia_Dialog_None)
    }

    companion object {
        private const val TAG = "DialogWindowAnimation"
        private const val ARG_ANIMATION_MODE = "animation_mode"

        fun newInstance(mode: WindowAnimationMode) = TestDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ANIMATION_MODE, mode.name)
            }
        }
    }

    private fun playEnterAnimation(view: View) {
        view.animate().cancel()

        view.alpha = 0f
        view.scaleX = 1f
        view.scaleY = 1f

        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.03f).apply {
            duration = 100L
            startDelay = 50L
        }

        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.03f).apply {
            duration = 100L
            startDelay = 50L
        }

        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            duration = 200L
            startDelay = 50L
        }

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            start()
        }
    }


    private fun playDismissAnimation(
        view: View,
        onEnd: () -> Unit
    ) {
        ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f).apply {
            duration = 200L

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })

            start()
        }
    }
}
