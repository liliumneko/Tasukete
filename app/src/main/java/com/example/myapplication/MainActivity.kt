package com.example.myapplication
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.example.myapplication.ui.alarm.TimeAnnounceReceiver
import com.example.myapplication.ui.main.TaskScreen
import com.example.myapplication.ui.main.TaskViewModel
import com.example.myapplication.ui.webview.WebViewScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val taskViewModel: TaskViewModel = viewModel()
                TaskScreen(taskViewModel)
                WebViewScreen()
            }
        }

        // 设置全屏模式
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }

        scheduleAlarm(this)
    }



    // ✅ 添加定时任务，每小时 59:45 触发
    private fun scheduleAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TimeAnnounceReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.SECOND, 45) // 设定秒数为 45
            set(Calendar.MILLISECOND, 0)

            val minute = get(Calendar.MINUTE)
            if (minute < 29 || (minute == 29 && get(Calendar.SECOND) < 45)) {
                // 当前时间还没到 29:45，则设置为本小时 29:45 触发
                set(Calendar.MINUTE, 29)
            } else if (minute < 59 || (minute == 59 && get(Calendar.SECOND) < 45)) {
                // 当前时间已经过了 29:45，但还没到 59:45，则设置为 59:45 触发
                set(Calendar.MINUTE, 59)
            } else {
                // 当前时间已经超过 59:45，则跳到下个小时的 29:45
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 29)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                // 没有权限，使用非精确闹钟
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } else {
            // 低于 Android 12，直接使用精确闹钟
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
}