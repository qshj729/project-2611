@file:OptIn(ExperimentalMaterial3Api::class)

package com.hubdeck.app.ui.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.hubdeck.app.core.model.EndpointScope
import com.hubdeck.app.core.model.HealthMode

/**
 * 新建 / 编辑服务界面（纯函数式）。
 *
 * 重点：新建时「按 MagicDNS 后缀生成 Tailnet 地址」——
 * 短名由 C-05 的渲染器补成 FQDN，界面这里只负责生成草稿与显示预览。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceEditContent(
    uiState: ServiceEditUiState,
    onNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onPinnedChange: (Boolean) -> Unit,
    onHealthModeChange: (HealthMode) -> Unit,
    onHealthPathChange: (String) -> Unit,
    onHealthTimeoutChange: (Int) -> Unit,
    onEndpointHostChange: (String, String) -> Unit,
    onEndpointPortChange: (String, String) -> Unit,
    onEndpointTemplateChange: (String, String) -> Unit,
    onEndpointScopeChange: (String, EndpointScope) -> Unit,
    onEndpointPrimaryChange: (String, Boolean) -> Unit,
    onAddEndpoint: (EndpointScope) -> Unit,
    onRemoveEndpoint: (String) -> Unit,
    onGenerateTailnetEndpoint: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = if (uiState.isNew) "新建服务" else "编辑服务") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = uiState.canSave) {
                        Text(text = "保存")
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
            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "服务名称") },
                singleLine = true,
            )

            if (uiState.categories.isNotEmpty()) {
                Text(text = "分类", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.categories.forEach { category ->
                        FilterChip(
                            selected = uiState.categoryId == category.id,
                            onClick = {
                                onCategorySelected(
                                    if (uiState.categoryId == category.id) null else category.id,
                                )
                            },
                            label = { Text(text = category.name) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "收藏置顶", modifier = Modifier.weight(1f))
                Switch(checked = uiState.pinned, onCheckedChange = onPinnedChange)
            }

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "备注（可选）") },
                minLines = 2,
            )

            HorizontalDivider()
            Text(
                text = "访问地址",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            uiState.endpoints.forEach { draft ->
                EndpointDraftCard(
                    draft = draft,
                    magicDnsSuffix = uiState.magicDnsSuffix,
                    onHostChange = { onEndpointHostChange(draft.id, it) },
                    onPortChange = { onEndpointPortChange(draft.id, it) },
                    onTemplateChange = { onEndpointTemplateChange(draft.id, it) },
                    onScopeChange = { onEndpointScopeChange(draft.id, it) },
                    onPrimaryChange = { onEndpointPrimaryChange(draft.id, it) },
                    onRemove = { onRemoveEndpoint(draft.id) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onAddEndpoint(EndpointScope.LAN) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "添加地址")
                }
                OutlinedButton(
                    onClick = onGenerateTailnetEndpoint,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "生成 Tailnet 地址")
                }
            }
            uiState.magicDnsSuffix?.let { suffix ->
                Text(
                    text = "当前 MagicDNS 后缀：$suffix",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()
            Text(
                text = "健康检查",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthMode.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.healthMode == mode,
                        onClick = { onHealthModeChange(mode) },
                        label = {
                            Text(
                                text = when (mode) {
                                    HealthMode.NONE -> "不探测"
                                    HealthMode.TCP -> "TCP"
                                    HealthMode.HTTP -> "HTTP"
                                },
                            )
                        },
                    )
                }
            }
            if (uiState.healthMode != HealthMode.NONE) {
                OutlinedTextField(
                    value = uiState.healthPath,
                    onValueChange = onHealthPathChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "探测路径") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.healthTimeoutMs.toString(),
                    onValueChange = { onHealthTimeoutChange(it.toIntOrNull() ?: 3_000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "超时（毫秒）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            Button(
                onClick = onSave,
                enabled = uiState.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (uiState.isSaving) "保存中…" else "保存")
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(text = "放弃修改")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EndpointDraftCard(
    draft: EndpointDraft,
    magicDnsSuffix: String?,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTemplateChange: (String) -> Unit,
    onScopeChange: (EndpointScope) -> Unit,
    onPrimaryChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EndpointScope.entries.forEach { scope ->
                        FilterChip(
                            selected = draft.scope == scope,
                            onClick = { onScopeChange(scope) },
                            label = { Text(text = scope.displayLabel()) },
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "删除这个地址")
                }
            }

            OutlinedTextField(
                value = draft.host,
                onValueChange = onHostChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "主机名") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.port,
                    onValueChange = onPortChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(text = "端口（可选）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = draft.urlTemplate,
                    onValueChange = onTemplateChange,
                    modifier = Modifier.weight(2f),
                    label = { Text(text = "URL 模板") },
                    singleLine = true,
                )
            }
            Text(
                text = "预览：${draft.previewUrl(magicDnsSuffix)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = draft.isPrimary, onCheckedChange = onPrimaryChange)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "同网络类型内的首选地址", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}