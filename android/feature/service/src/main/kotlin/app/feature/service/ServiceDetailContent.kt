@file:OptIn(ExperimentalMaterial3Api::class)

package app.feature.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.core.model.EndpointScope
import app.core.model.HealthSample

/**
 * 服务详情入口（Hilt 版）。
 *
 * @param onOpenUrl 打开服务（Custom Tab）——由装配壳接 C-11 的 `ServiceOpener`。
 */
@Composable
fun ServiceDetailRoute(
    serviceId: String,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ServiceDetailViewModel = hiltViewModel()
    val editViewModel: ServiceEditViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editState by editViewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(serviceId) {
        viewModel.load(serviceId)
        editViewModel.load(serviceId)
    }

    androidx.compose.runtime.LaunchedEffect(editState.saved) {
        if (editState.saved) {
            viewModel.reload()
        }
    }

    if (state.isEditing) {
        ServiceEditContent(
            uiState = editState,
            onNameChange = editViewModel::onNameChange,
            onNotesChange = editViewModel::onNotesChange,
            onCategorySelected = editViewModel::onCategorySelected,
            onPinnedChange = editViewModel::onPinnedChange,
            onHealthModeChange = editViewModel::onHealthModeChange,
            onHealthPathChange = editViewModel::onHealthPathChange,
            onHealthTimeoutChange = editViewModel::onHealthTimeoutChange,
            onEndpointHostChange = editViewModel::onEndpointHostChange,
            onEndpointPortChange = editViewModel::onEndpointPortChange,
            onEndpointTemplateChange = editViewModel::onEndpointTemplateChange,
            onEndpointScopeChange = editViewModel::onEndpointScopeChange,
            onEndpointPrimaryChange = editViewModel::onEndpointPrimaryChange,
            onAddEndpoint = editViewModel::onAddEndpoint,
            onRemoveEndpoint = editViewModel::onRemoveEndpoint,
            onGenerateTailnetEndpoint = editViewModel::onGenerateTailnetEndpoint,
            onSave = editViewModel::save,
            onCancel = viewModel::cancelEditing,
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    ServiceDetailContent(
        uiState = state,
        onSelectEndpoint = viewModel::onSelectEndpoint,
        onUseAutoEndpoint = viewModel::onUseAutoEndpoint,
        onProbe = viewModel::probe,
        onEdit = viewModel::startEditing,
        onDelete = { viewModel.delete(onBack) },
        onOpenUrl = onOpenUrl,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * 服务详情界面（纯函数式）。
 *
 * 无障碍：端点切换用 [selectable]，TalkBack 会读出「已选中」；
 * 打开按钮用整块可点击区域 + 明确文案。
 */
@Composable
fun ServiceDetailContent(
    uiState: ServiceDetailUiState,
    onSelectEndpoint: (String) -> Unit,
    onUseAutoEndpoint: () -> Unit,
    onProbe: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.service?.name ?: "服务",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (!uiState.isNew) {
                        IconButton(onClick = onEdit) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "编辑服务")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "删除服务")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
                return@Column
            }

            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (uiState.isNew) {
                Text(
                    text = "这是一个新服务，请在编辑界面填写名称与访问地址。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "去填写")
                }
                return@Column
            }

            // 打开服务（Custom Tab）
            Button(
                onClick = { onOpenUrl(uiState.url) },
                enabled = uiState.canOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "打开服务，使用系统浏览器"
                    },
            ) {
                Text(text = "打开服务")
            }
            if (uiState.url.isNotBlank()) {
                Text(
                    text = uiState.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 端点回退 / 手动选择提示
            if (uiState.fellBack) {
                HintCard(
                    text = "首选地址不可达，已自动回退到备用地址。",
                    isError = true,
                )
            } else if (uiState.manualSelection) {
                HintCard(text = "当前使用的是手动选择的地址。", isError = false)
            }

            // 端点列表
            Text(
                text = "访问地址",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            uiState.endpoints.forEach { endpointUi ->
                EndpointRow(
                    endpointUi = endpointUi,
                    onClick = { onSelectEndpoint(endpointUi.endpoint.id) },
                )
            }
            if (uiState.manualSelection) {
                TextButton(onClick = onUseAutoEndpoint) {
                    Text(text = "跟随网络自动选择")
                }
            }

            // 探测状态
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "可达性", style = MaterialTheme.typography.titleMedium)
                    val probe = uiState.probeResult
                    Text(
                        text = when {
                            probe == null -> "尚未探测"
                            probe.reachable -> "可达，耗时 ${probe.latencyMs ?: 0} ms"
                            else -> probe.reason ?: "不可达"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (probe?.reachable == true) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                OutlinedButton(onClick = onProbe) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "探测")
                }
            }

            // 最近探测历史
            if (uiState.healthSamples.isNotEmpty()) {
                Text(
                    text = "最近探测记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                uiState.healthSamples.take(MAX_HISTORY_ROWS).forEach { sample ->
                    HealthSampleRow(sample = sample)
                }
            }

            // 元信息
            val service = uiState.service
            if (service != null) {
                HorizontalDivider()
                Text(text = "备注", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = service.notes?.takeIf { it.isNotBlank() } ?: "（无）",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(text = "删除这个服务？") },
            text = { Text(text = "服务及其访问地址会从本机移除，无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text(text = "删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(text = "取消")
                }
            },
        )
    }
}

@Composable
private fun EndpointRow(
    endpointUi: EndpointUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = endpointUi.isSelected, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (endpointUi.isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = endpointUi.endpoint.scope.displayLabel(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (endpointUi.isPreferred) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "当前网络推荐",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = endpointUi.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HealthSampleRow(
    sample: HealthSample,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (sample.reachable) "可达" else (sample.reason ?: "不可达"),
            style = MaterialTheme.typography.bodyMedium,
            color = if (sample.reachable) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = sample.latencyMs?.let { "$it ms" } ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HintCard(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** scope 的中文标签。 */
fun EndpointScope.displayLabel(): String = when (this) {
    EndpointScope.LAN -> "局域网"
    EndpointScope.TAILNET -> "Tailnet"
    EndpointScope.PUBLIC -> "公网"
}

/** scope 的中文标签（设计文档里的「地址标签」）。 */
fun EndpointScope.displayText(): String = when (this) {
    EndpointScope.LAN -> "局域网"
    EndpointScope.TAILNET -> "Tailnet"
    EndpointScope.PUBLIC -> "公网"
}

const val MAX_HISTORY_ROWS = 8