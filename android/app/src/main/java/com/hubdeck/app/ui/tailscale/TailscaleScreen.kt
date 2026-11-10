package com.hubdeck.app.ui.tailscale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hubdeck.app.core.model.TailnetDevice
import com.hubdeck.app.core.model.TailnetState
import com.hubdeck.app.core.tailscale.TailnetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TailscaleViewModel @Inject constructor(
    private val tailnetRepository: TailnetRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TailnetState())
    val state: StateFlow<TailnetState> = _state

    private val _devices = MutableStateFlow<List<TailnetDevice>>(emptyList())
    val devices: StateFlow<List<TailnetDevice>> = _devices

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun refresh() {
        viewModelScope.launch {
            _state.value = tailnetRepository.refreshLocalState()
            val tailnet = _state.value.tailnetName
            _devices.value = if (tailnet.isNullOrBlank()) emptyList() else tailnetRepository.devices(tailnet)
        }
    }

    fun saveLocalApiSecret(secret: String) {
        viewModelScope.launch {
            tailnetRepository.storeLocalApiSecret(secret)
            _message.value = "已保存本机 Local API secret，正在重新读取状态…"
            refresh()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TailscaleScreen(
    onBack: () -> Unit,
    viewModel: TailscaleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var secret by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Tailscale") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("连接状态", style = MaterialTheme.typography.titleMedium)
                    Text(if (state.connected) "已加入 tailnet" else "未检测到本机 tailnet 连接")
                    Text("后端状态：${state.backendState ?: "-"}")
                    Text("MagicDNS 后缀：${state.magicDnsSuffix ?: "-"}")
                    Text("本机地址：${state.selfIps.joinToString(", ").ifBlank { "-" }}")
                    Text("来源：${state.source}")
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Local API secret（可选）", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "仅用于读取本机 tailnet 状态，不提供也能正常使用。\n" +
                            "获取方式：在任意已登录的电脑执行 `tailscale debug prefs`，复制其中的 Local API Secret。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = { Text("Local API Secret") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    Button(
                        onClick = { viewModel.saveLocalApiSecret(secret.trim()); secret = "" },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("保存并刷新")
                    }
                }
            }

            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Text("tailnet 设备", style = MaterialTheme.typography.titleMedium)
            if (devices.isEmpty()) {
                Text("未获取到设备列表（需要控制平面 OAuth 凭据，roadmap M3.1）。")
            } else {
                devices.forEach { device ->
                    Text("· ${device.name} ${if (device.online) "在线" else "离线"} ${device.dnsName.orEmpty()}")
                }
            }
        }
    }
}
