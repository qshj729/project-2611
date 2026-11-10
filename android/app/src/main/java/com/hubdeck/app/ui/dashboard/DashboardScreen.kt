package com.hubdeck.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hubdeck.app.core.model.EndpointScope
import com.hubdeck.app.core.model.NetworkContext
import com.hubdeck.app.core.model.Service
import com.hubdeck.app.ui.ServiceOpener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenService: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTailscale: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.services) { viewModel.probeVisible(state.services.take(12)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HubDeck") },
                actions = {
                    TailnetBadge(
                        connected = state.tailnet.connected,
                        networkContext = state.networkContext,
                        onClick = onOpenTailscale,
                    )
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = true,
                label = { Text("搜索服务") },
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategoryId == null,
                        onClick = { viewModel.onCategorySelected(null) },
                        label = { Text("全部") },
                    )
                }
                items(state.categories) { category ->
                    FilterChip(
                        selected = state.selectedCategoryId == category.id,
                        onClick = { viewModel.onCategorySelected(category.id) },
                        label = { Text(category.name) },
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.services, key = { it.id }) { service ->
                    ServiceCard(
                        service = service,
                        statusLabel = state.health[service.id]?.let { result ->
                            if (result.reachable) "在线" else result.reason ?: "不可达"
                        },
                        onClick = {
                            val url = viewModel.urlFor(service)
                            if (url != null) {
                                ServiceOpener.open(context, url)
                            } else {
                                onOpenService(service.id)
                            }
                        },
                        onLongClick = { onOpenService(service.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceCard(
    service: Service,
    statusLabel: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${service.name}，状态 ${statusLabel ?: "未知"}" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = service.iconValue?.takeIf { it.isNotBlank() } ?: service.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = service.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor(statusLabel)),
                )
                Text(
                    text = statusLabel ?: "未探测",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                text = scopeLabel(service),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TailnetBadge(
    connected: Boolean,
    networkContext: NetworkContext,
    onClick: () -> Unit,
) {
    val label = when {
        !connected -> "未入网"
        networkContext == NetworkContext.HOME -> "在家"
        networkContext == NetworkContext.AWAY -> "在外"
        else -> "已连接"
    }
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.VpnKey,
            contentDescription = "Tailscale：$label",
            tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

private fun scopeLabel(service: Service): String = when {
    service.endpoints.isEmpty() -> "未配置地址"
    else -> service.endpoints.joinToString(" · ") {
        when (it.scope) {
            EndpointScope.LAN -> "局域网"
            EndpointScope.TAILNET -> "Tailnet"
            EndpointScope.PUBLIC -> "公网"
        }
    }
}

private fun statusColor(statusLabel: String?): Color = when {
    statusLabel == null -> Color(0xFF94A3B8)
    statusLabel == "在线" -> Color(0xFF22C55E)
    else -> Color(0xFFEF4444)
}
