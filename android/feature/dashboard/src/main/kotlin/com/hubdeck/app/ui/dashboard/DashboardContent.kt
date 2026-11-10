@file:OptIn(ExperimentalMaterial3Api::class)

package com.hubdeck.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hubdeck.app.core.model.EndpointScope
import com.hubdeck.app.core.model.IconKind
import com.hubdeck.app.core.model.NetworkContext
import com.hubdeck.app.core.model.Service

/**
 * 仪表盘入口（Hilt 版）。
 *
 * 页面不碰 NavController：所有导航动作通过回调交给装配壳（契约 C-12）。
 */
@Composable
fun DashboardRoute(
    onOpenService: (String) -> Unit,
    onAddService: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTailscale: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onFilterSelected = viewModel::onFilterSelected,
        onRefresh = viewModel::refresh,
        onOpenService = onOpenService,
        onAddService = onAddService,
        onOpenSettings = onOpenSettings,
        onOpenTailscale = onOpenTailscale,
        modifier = modifier,
    )
}

/**
 * 仪表盘界面（纯函数式：状态进、回调出）。
 *
 * 无障碍要点：
 * - 搜索框有可见 label 与语义；
 * - 分类 chip 用 [FilterChip]，TalkBack 可读出「已选中」；
 * - 卡片整体是一个可点击节点，语义里带服务名与状态。
 */
@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    onQueryChange: (String) -> Unit,
    onFilterSelected: (CategoryFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenService: (String) -> Unit,
    onAddService: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTailscale: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onAddService) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建服务",
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(text = "HubDeck", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    TailnetStatusAction(
                        uiState = uiState,
                        onOpenTailscale = onOpenTailscale,
                    )
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NetworkContextBanner(
                context = uiState.networkContext,
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
            )

            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text(text = "搜索服务") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清除搜索",
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            CategoryFilterRow(
                categories = uiState.categories.map { it.id to it.name },
                selected = uiState.filter,
                onSelect = onFilterSelected,
            )

            if (uiState.isEmpty) {
                EmptyDashboard(modifier = Modifier.fillMaxSize())
            } else {
                ServiceGrid(
                    cards = uiState.services,
                    onOpenService = onOpenService,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TailnetStatusAction(
    uiState: DashboardUiState,
    onOpenTailscale: () -> Unit,
) {
    val connected = uiState.tailnet.connected
    val description = if (connected) "Tailscale 已连接，点查看详情" else "Tailscale 未连接，点击查看详情"
    IconButton(onClick = onOpenTailscale) {
        Icon(
            imageVector = Icons.Default.VpnKey,
            contentDescription = description,
            tint = if (connected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun NetworkContextBanner(
    context: NetworkContext,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val (label, container) = when (context) {
        NetworkContext.HOME -> "在家" to MaterialTheme.colorScheme.secondaryContainer
        NetworkContext.AWAY -> "在外" to MaterialTheme.colorScheme.tertiaryContainer
        NetworkContext.UNKNOWN -> "网络模式未知" to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = container,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "当前网络：$label",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新网络状态与服务可达性",
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<Pair<String, String>>,
    selected: CategoryFilter,
    onSelect: (CategoryFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "filter-all") {
            FilterChip(
                selected = selected == CategoryFilter.All,
                onClick = { onSelect(CategoryFilter.All) },
                label = { Text(text = "全部") },
            )
        }
        item(key = "filter-favorites") {
            FilterChip(
                selected = selected == CategoryFilter.Favorites,
                onClick = { onSelect(CategoryFilter.Favorites) },
                label = { Text(text = "收藏") },
                leadingIcon = {
                    Icon(
                        imageVector = if (selected == CategoryFilter.Favorites) {
                            Icons.Default.Star
                        } else {
                            Icons.Default.StarBorder
                        },
                        contentDescription = null,
                    )
                },
            )
        }
        items(categories, key = { it.first }) { (id, name) ->
            FilterChip(
                selected = selected == CategoryFilter.ByCategory(id),
                onClick = { onSelect(CategoryFilter.ByCategory(id)) },
                label = { Text(text = name) },
            )
        }
    }
}

@Composable
private fun ServiceGrid(
    cards: List<ServiceCardUi>,
    onOpenService: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        state = gridState,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = cards, key = { it.service.id }) { card ->
            ServiceCard(
                card = card,
                onClick = { onOpenService(card.service.id) },
            )
        }
    }
}

@Composable
private fun ServiceCard(
    card: ServiceCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val service = card.service
    val stateText = card.badge.displayText()
    val scopeText = card.scope?.displayText()
    val description = buildString {
        append(service.name)
        if (scopeText != null) append("，$scopeText 地址")
        append("，$stateText")
        if (card.fellBack) append("，已回退到备用地址")
        append("，打开详情")
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clearAndSetSemantics {
                contentDescription = description
            },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceIcon(service = service)
                Spacer(modifier = Modifier.weight(1f))
                HealthBadgeDot(badge = card.badge)
            }
            Text(
                text = service.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (scopeText != null) {
                Text(
                    text = scopeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.fellBack) {
                Text(
                    text = "已回退到备用地址",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ServiceIcon(
    service: Service,
    modifier: Modifier = Modifier,
) {
    val emoji = service.iconValue.takeIf { service.iconKind == IconKind.EMOJI && !it.isNullOrBlank() }
    if (emoji != null) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.headlineSmall,
            modifier = modifier.size(32.dp),
        )
    } else {
        Box(
            modifier = modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun HealthBadgeDot(
    badge: HealthBadge,
    modifier: Modifier = Modifier,
) {
    val color = badge.tint(MaterialTheme.colorScheme)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = badge.displayText(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun EmptyDashboard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Dns,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = "还没有服务",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "在设置里导入 hubdeck.yaml，或用下方的「+」新建一个服务。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 徽标文案（中文）。 */
fun HealthBadge.displayText(): String = when (this) {
    HealthBadge.NOT_PROBED -> "未探测"
    HealthBadge.REACHABLE -> "已连接"
    HealthBadge.UNREACHABLE -> "不可达"
    HealthBadge.DISABLED -> "未启用探测"
}

@Composable
private fun HealthBadge.tint(scheme: androidx.compose.material3.ColorScheme): Color = when (this) {
    HealthBadge.REACHABLE -> scheme.primary
    HealthBadge.UNREACHABLE -> scheme.error
    HealthBadge.NOT_PROBED -> scheme.onSurfaceVariant
    HealthBadge.DISABLED -> scheme.outline
}

/** scope 的中文标签（设计文档里的「地址标签」）。 */
fun EndpointScope.displayText(): String = when (this) {
    EndpointScope.LAN -> "局域网"
    EndpointScope.TAILNET -> "Tailnet"
    EndpointScope.PUBLIC -> "公网"
}

@Preview(showBackground = true)
@Composable
private fun DashboardContentPreview() {
    DashboardContent(
        uiState = DashboardUiState(
            services = listOf(
                ServiceCardUi(
                    service = Service(id = "1", name = "Jellyfin"),
                    badge = HealthBadge.REACHABLE,
                    scope = EndpointScope.TAILNET,
                    fellBack = false,
                ),
            ),
            networkContext = NetworkContext.AWAY,
        ),
        onQueryChange = {},
        onFilterSelected = {},
        onRefresh = {},
        onOpenService = {},
        onAddService = {},
        onOpenSettings = {},
        onOpenTailscale = {},
    )
}
