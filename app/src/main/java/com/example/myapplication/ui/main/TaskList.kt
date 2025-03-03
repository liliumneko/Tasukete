package com.example.myapplication.ui.main
import android.app.*
import android.content.*
import android.media.Ringtone
import android.media.RingtoneManager
import android.util.Log
import android.view.WindowManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import java.text.SimpleDateFormat
import java.util.*

data class Task(
    val id: Int = 0,
    val date: String?,
    val title: String,
    val description: String,
    var completed: Boolean = false
)

data class ServerStatus(val status: String)

interface ApiService {
    @GET("/api/tasks")
    suspend fun getTasks(): List<Task>

    @PUT("/api/tasks/update")
    suspend fun updateTask(@Body task: Task): Task

    @GET("/api/server/status")
    suspend fun getServerStatus(): ServerStatus
}

val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl("http://10.0.2.2:3000/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val apiService: ApiService = retrofit.create(ApiService::class.java)

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val _tasks = mutableStateListOf<Task>()
    val tasks: List<Task> get() = _tasks

    private val _serverStatus = mutableStateOf("离线")
    val serverStatus: State<String> get() = _serverStatus

    private val appContext: Context = application.applicationContext

    init {
        refreshData()
        startTaskDeadlineChecker()
    }

    private fun refreshData() {
         viewModelScope.launch {
             repeat(Int.MAX_VALUE) {
                 if (!isActive) return@launch
            try {
                _tasks.clear()
                val response = apiService.getTasks()
                _tasks.addAll(response)
                val statusResponse = apiService.getServerStatus()
                _serverStatus.value = statusResponse.status
            } catch (e: Exception) {
                Log.e("API_ERROR", "网络请求失败", e)
                _serverStatus.value = "离线"
            }
                 delay(5000)
             }
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            try {
                val updatedTask = task.copy(completed = !task.completed)
                apiService.updateTask(updatedTask)
                val index = _tasks.indexOfFirst { it.id == task.id }
                if (index != -1) {
                    _tasks[index] = updatedTask
                }
            } catch (e: Exception) {
                Log.e("ERROR", "更新任务失败", e)
            }
        }
    }

    private fun startTaskDeadlineChecker() {
        viewModelScope.launch {
            while (true) {
                delay(20000) // 每 1 秒检查一次任务
                checkTaskDeadlines()
            }
        }
    }
    private val sharedPreferences = appContext.getSharedPreferences("TaskPrefs", Context.MODE_PRIVATE)

    private fun hasTaskBeenNotified(taskId: Int): Boolean {
        return sharedPreferences.getBoolean("task_$taskId", false)
    }

    private fun markTaskAsNotified(taskId: Int) {
        sharedPreferences.edit().putBoolean("task_$taskId", true).apply()
    }
    private fun checkTaskDeadlines() {
        val currentTime = System.currentTimeMillis()
        val timeThreshold = currentTime - (10 * 60 * 1000) // 过去 10 分钟的任务不触发

        for (task in _tasks) {
            val taskTime = task.date?.let { parseDateToMillis(it) }
            if (taskTime != null && taskTime in timeThreshold..currentTime && !task.completed && !hasTaskBeenNotified(task.id)) {
                triggerReminder(task)
                markTaskAsNotified(task.id) // 记录该任务已提醒
            }
        }
    }
    private fun triggerReminder(task: Task) {
        val intent = Intent(appContext, FullScreenReminderActivity::class.java).apply {
            putExtra("TASK_TITLE", task.title)
            putExtra("TASK_DESCRIPTION", task.description)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        appContext.startActivity(intent)

    }

    private fun parseDateToMillis(dateString: String): Long? {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            format.timeZone = TimeZone.getDefault()
            format.parse(dateString)?.time
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun TaskScreen(viewModel: TaskViewModel) {
    val tasks = viewModel.tasks
    val serverStatus by viewModel.serverStatus
    val completedCount = tasks.count { it.completed }
    val uncompletedCount = tasks.size - completedCount
    var currentTime by remember { mutableStateOf(getCurrentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000) // 每秒更新一次
            currentTime = getCurrentTime()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tasks) { task ->
                TaskRow(task) { viewModel.toggleTaskCompletion(task) }
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
            }
        }

        HorizontalDivider(modifier = Modifier.fillMaxWidth())

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = currentTime, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(text = "已完成：$completedCount  未完成：$uncompletedCount", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(text = "服务器状态：$serverStatus", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}
fun getCurrentTime(): String {
    return SimpleDateFormat("EEEE HH:mm", Locale.CHINA).format(Date())
}
@Composable
fun TaskRow(task: Task, onToggleCompletion: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { onToggleCompletion() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = task.date ?: "无限期任务", fontSize = 16.sp, fontWeight = FontWeight.Light)
            Text(text = task.title.ifEmpty { "无标题" }, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(text = task.description.ifEmpty { "无备注" }, fontSize = 16.sp, fontWeight = FontWeight.Light)
        }

        Text(
            text = if (task.completed) "已完成" else "未完成",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
class FullScreenReminderActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 强制全屏（兼容旧版 Android）
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // 获取任务信息
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "空提醒"
        val taskDescription = intent.getStringExtra("TASK_DESCRIPTION") ?: "无描述"

        setContent {
            ReminderScreen(
                title = taskTitle,
                description = taskDescription,
                onDismiss = { dismissReminder() }
            )
        }

        // 直接播放系统默认的闹钟铃声
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
        ringtone?.play()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.let { controller ->
                controller.hide(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars() // ✅ 仅适用于 Android 11+
                )
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
        }
    }
    private fun dismissReminder() {
        ringtone?.stop()
        ringtone = null
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        ringtone?.stop()
    }
}

@Composable
fun ReminderScreen(title: String, description: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = title,
                fontSize = 200.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                fontSize = 60.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = { onDismiss() },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                Text(text = "好的", color = Color.White, fontSize = 50.sp)
            }
        }
    }
}
