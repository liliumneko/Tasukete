package com.example.myapplication.ui.theme
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import java.util.Calendar

class TimeAnnounceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // 设备重启后重新安排闹钟
                scheduleAlarm(context)
                Log.d("TimeAnnounceReceiver", "Boot completed: Alarm rescheduled")
            }
            else -> {
                // 处理正常的定时闹钟事件
                val activityIntent = Intent(context, TimeAnnounceActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(activityIntent)
            }
        }
    }
}
fun scheduleAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, TimeAnnounceReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val calendar = Calendar.getInstance().apply {
        set(Calendar.SECOND, 45)
        set(Calendar.MILLISECOND, 0)

        val minute = get(Calendar.MINUTE)
        when {
            minute < 29 || (minute == 29 && get(Calendar.SECOND) < 45) -> {
                set(Calendar.MINUTE, 29)
            }
            minute in 29..58 || (minute == 59 && get(Calendar.SECOND) < 45) -> {
                set(Calendar.MINUTE, 59)
            }
            else -> {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 29)
            }
        }
    }

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d("AlarmScheduler", "Exact alarm set for: ${calendar.time}")
            } else {
                Log.w("AlarmScheduler", "Exact alarms not allowed, using normal set()")
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } else {
            // 低于 Android 12，直接使用 setExactAndAllowWhileIdle()
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            Log.d("AlarmScheduler", "Exact alarm set for: ${calendar.time}")
        }
    } catch (e: SecurityException) {
        Log.e("AlarmScheduler", "SecurityException: Exact alarms not allowed", e)
    }
}