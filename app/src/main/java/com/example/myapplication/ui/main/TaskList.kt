package com.example.myapplication.ui.main

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

// **数据模型**
data class Task(
    val id: Int = 0,
    val date: String?, // 解决 null 问题
    val title: String,
    val description: String,
    var completed: Boolean = false
)
data class ServerStatus(val status: String)

// **Retrofit API 接口**
interface ApiService {
    @GET("/api/tasks")  // 修改路径
    suspend fun getTasks(): List<Task>

    @PUT("/api/tasks/update")
    suspend fun updateTask(@Body task: Task): Task

    @GET("/api/server/status")
    suspend fun getServerStatus(): ServerStatus
}


// **Retrofit 实例**
val retrofit = Retrofit.Builder()
    .baseUrl("http://192.168.123.144:3000/")  // 替换为你的后端 URL
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val apiService: ApiService = retrofit.create(ApiService::class.java)

// **ViewModel**
class TaskViewModel : ViewModel() {
    private val _tasks = mutableStateListOf<Task>()
    val tasks: List<Task> get() = _tasks

    private val _serverStatus = mutableStateOf("离线")
    val serverStatus: State<String> get() = _serverStatus

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            try {
                _tasks.clear()
                val response = apiService.getTasks()
                _tasks.addAll(response)

                val statusResponse = apiService.getServerStatus()
                _serverStatus.value = statusResponse.status
            } catch (e: Exception) {
                Log.e("API_ERROR", "网络请求失败", e)  // 添加日志
                _serverStatus.value = "离线"
            }
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            try {
                val updatedTask = task.copy(completed = !task.completed)
                apiService.updateTask(updatedTask) // 调用 API 更新服务器
                val index = _tasks.indexOfFirst { it.id == task.id }
                if (index != -1) {
                    _tasks[index] = updatedTask // 本地更新 UI
                }
            } catch (e: Exception) {
                // 可以添加错误处理逻辑
            }
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
                Divider(modifier = Modifier.fillMaxWidth())
            }
        }

        Divider(modifier = Modifier.fillMaxWidth())

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
    return SimpleDateFormat("EEEE HH:mm:ss", Locale.CHINA).format(Date())
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
            Text(text = task.date ?: "无限期任务", fontSize = 16.sp, fontWeight = FontWeight.Light) // ✅ 处理 null
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