package com.hubdeck.app.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

data class AuthKeyScreenState(
    val storedKey: String? = null,
    val input: String = "",
    val showInput: Boolean = true,
)

@Composable
fun AuthKeyInputScreen(
    onCompleted: () -> Unit = {},
) {
    var input by remember { mutableStateOf("") }
    var showInput by remember { mutableStateOf(true) }

    val appContext = LocalContext.current?.applicationContext
        ?: throw IllegalStateException("Application context not available")

    val sharedPrefs = appContext.getSharedPreferences("hubdeck_prefs", android.content.Context.MODE_PRIVATE)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dp(16f)),
    ) {
        if (showInput) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(Dp(16f))) {
                    Text("输入 Tailscale Auth Key 以连接内网", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "这将用于在应用进程内建立 tsnet 节点，访问 tailnet 内网地址。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("Auth Key") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dp(8f)),
                    )
                    Button(
                        onClick = {
                            val key = input.trim()
                            if (key.isNotEmpty() && key != "placeholder-key-no-env") {
                                sharedPrefs.edit().putString("tailscale_auth_key_saved", key).apply()
                                showInput = false
                                onCompleted()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(Dp(8f)),
                    ) {
                        Text("保存并继续")
                    }
                }
            }

            Button(
                onClick = { onCompleted() },
                modifier = Modifier.fillMaxWidth().padding(Dp(4f)),
            ) {
                Text("跳过（使用占位符 key）")
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(Dp(16f))) {
                    Text("已保存 Auth Key", style = MaterialTheme.typography.titleMedium)
                    Text("Auth Key 已存储，应用将使用此 key 初始化 tsnet 节点")
                    Button(
                        onClick = {
                            showInput = true
                            input = ""
                        },
                        modifier = Modifier.fillMaxWidth().padding(Dp(8f)),
                    ) {
                        Text("重新输入 Key")
                    }
                }
            }
        }
    }
}