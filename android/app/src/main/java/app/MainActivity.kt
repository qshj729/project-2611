package app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ui.MyAppsApp
import app.ui.theme.MyAppsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyAppsTheme {
                TunnelAwareApp()
            }
        }
    }
}

/**
 * 应用外壳：启动隧道（有 auth key 时自动连接），并渲染主界面。
 */
@Composable
fun TunnelAwareApp() {
    val context = LocalContext.current.applicationContext

    val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        context, app.di.MyAppsApplicationEntryPoint::class.java
    )
    val tunnelManager = entryPoint.tunnelManager()

    // 首次组合时尝试启动隧道（内部读 Keystore 里的 auth key）
    val started = remember { mutableStateOf(false) }
    if (!started.value) {
        started.value = true
        tunnelManager.startIfConfigured()
    }

    // 订阅隧道状态（供 UI 显示）
    val tunnelState by tunnelManager.tunnelState.collectAsStateWithLifecycle()
    // 订阅授权链接（供状态条点击打开）
    val authUrl by tunnelManager.authUrl.collectAsStateWithLifecycle()

    // 服务清单存储由 Hilt 提供（单例 DataStore，不能重复创建）
    val servicesStore = entryPoint.internalServicesStore()
    MyAppsApp(servicesStore = servicesStore, tunnelState = tunnelState, authUrl = authUrl)
}
