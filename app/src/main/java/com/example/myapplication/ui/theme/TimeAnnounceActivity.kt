package com.example.myapplication.ui.theme
import android.app.Activity
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import java.util.Locale

class TimeAnnounceActivity : Activity() {
    private lateinit var timeView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置全屏模式
        hideSystemUI()

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val layout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        timeView = TextView(this).apply {
            textSize = 160f
            setTextColor(0xFF000000.toInt())
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }

        layout.addView(timeView)
        setContentView(layout)

        handler.post(updateTimeRunnable)

        // 15 秒后播放 "哔哔" 声
        handler.postDelayed({ playBeepSound() }, 15_000)

        // 30 秒后自动关闭 Activity
        handler.postDelayed({ finish() }, 30_000)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI() // 确保恢复时保持全屏
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI() // 重新进入全屏
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateTimeRunnable)
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.release()
    }

    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            timeView.text = getCurrentTime()
            handler.postDelayed(this, 1000)
        }
    }

    private fun getCurrentTime(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        return String.format(Locale.US, "%02d:%02d:%02d", hour, minute, second)
    }

    private fun playBeepSound() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        if (hour in 20..23 || hour in 0..8) {
            return // 20:00 - 09:00 不播放声音
        }

        mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
        mediaPlayer?.setOnCompletionListener {
            hideSystemUI() // 播放结束后隐藏导航栏
        }
        mediaPlayer?.start()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
    }
}