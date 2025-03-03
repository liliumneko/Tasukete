package com.example.myapplication.ui.webview

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 定义服务器返回的数据结构
data class WebViewResponse(
    val showWebView: Boolean,
    val url: String?
)

// 定义 Retrofit 接口
interface ApiService {
    @GET("/api/webview") // 替换为你的 API 路径
    suspend fun getWebViewConfig(): WebViewResponse
}

object RetrofitClient {
    private const val BASE_URL = "http://192.168.123.144:3000/"

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

// ViewModel 负责获取数据
class WebViewViewModel : ViewModel() {
    var webViewState by mutableStateOf<WebViewResponse?>(null)
        private set

    fun fetchWebViewConfig(retryCount: Int = 3) {
        viewModelScope.launch {
            var attempts = 0
            while (attempts < retryCount) {
                try {
                    webViewState = RetrofitClient.apiService.getWebViewConfig()
                    return@launch // 成功获取数据，退出循环
                } catch (e: Exception) {
                    e.printStackTrace()
                    attempts++
                    delay(2000) // 2秒后重试
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(viewModel: WebViewViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val webViewState = viewModel.webViewState
    var isError by remember { mutableStateOf(false) }

    // 加载数据
    LaunchedEffect(Unit) {
        viewModel.fetchWebViewConfig()
    }

    if (webViewState == null) {
        // 显示加载中
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {

        }
    } else if (webViewState.showWebView) {  // 只在 showWebView 为 true 时显示 WebView
        val coroutineScope = rememberCoroutineScope()

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                isError = true
                                coroutineScope.launch {
                                    delay(2000) // 2秒后重试
                                    isError = false
                                    loadUrl(webViewState.url ?: "")
                                }
                            }
                        }
                    }
                    loadUrl(webViewState.url ?: "")
                }
            }
        )

        if (isError) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column {
                    Text(text = "加载失败，正在重试...", style = MaterialTheme.typography.titleLarge)
                    CircularProgressIndicator()
                }
            }
        }
    }
}