package com.hubdeck.app.ui.settings

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hubdeck.app.core.data.importer.HomarrImporter
import com.hubdeck.app.core.data.importer.HubDeckYaml
import com.hubdeck.app.core.data.repo.ServiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ServiceRepository,
) : ViewModel() {

    private val _exportedYaml = MutableStateFlow<String?>(null)
    val exportedYaml: StateFlow<String?> = _exportedYaml

    private val _importSummary = MutableStateFlow<String?>(null)
    val importSummary: StateFlow<String?> = _importSummary

    fun export() {
        viewModelScope.launch {
            val services = repository.observeServices().firstOrNull() ?: emptyList()
            _exportedYaml.value = HubDeckYaml.export(services)
        }
    }

    fun importHomarr(yaml: String) {
        viewModelScope.launch {
            runCatching { HomarrImporter.parse(yaml) }
                .onSuccess { result ->
                    result.services.forEach { repository.upsert(it) }
                    _importSummary.value = "已导入 ${result.services.size} 个服务" +
                        if (result.warnings.isEmpty()) "" else "；${result.warnings.size} 条提示"
                }
                .onFailure { error ->
                    _importSummary.value = "导入失败：${error.message ?: "无法解析 YAML"}"
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val exported by viewModel.exportedYaml.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()
    var homarrYaml by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
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
                    Text("数据", style = MaterialTheme.typography.titleMedium)
                    Text("目录以 hubdeck.yaml 为准，可随时导出备份。")
                    Button(onClick = { viewModel.export() }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("导出 hubdeck.yaml")
                    }
                    exported?.let { yaml ->
                        Text(
                            text = yaml.take(800),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("从 Homarr 导入", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = homarrYaml,
                        onValueChange = { homarrYaml = it },
                        label = { Text("粘贴 Homarr config.yaml") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    Button(
                        onClick = { viewModel.importHomarr(homarrYaml) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("导入")
                    }
                    importSummary?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("网络", style = MaterialTheme.typography.titleMedium)
                    Text("家中 Wi-Fi 名称：用于判断「在家 / 在外」。未配置时依靠探测结果推断。")
                    Text("Tailscale secret 与探测参数见 roadmap M2。")
                }
            }
        }
    }
}
