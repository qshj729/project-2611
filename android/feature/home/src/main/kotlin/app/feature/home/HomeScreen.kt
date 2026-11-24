package app.feature.home

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.core.data.config.InternalServicesStore
import app.core.model.InternalService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 内网服务导航页（Home Screen）。
 *
 * 用 DataStore 里的 YAML 渲染分组卡片网格，点击 → 交给浏览器打开（走 PAC → 隧道代理）。
 *
 * 设计参考：AGENTS.md §8（C-02 清单 / C-08 导航页）/ contracts.md C-08
 */
@Composable
fun HomeScreen(
    servicesStore: InternalServicesStore,
    onOpenService: (String) -> Unit,   // url → 交给浏览器屏
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val services by servicesStore.observe().distinctUntilChanged().collectAsStateWithLifecycle(
        lifecycleOwner = lifecycleOwner,
        initialValue = emptyList(),
    )

    // 将分组数据扁平化为带标题的项列表
    val displayItems = remember(services) {
        val grouped = services.groupBy { it.group ?: "未分组" }.toList()
            .sortedBy { it.first }
        val result = mutableListOf<Any>()
        grouped.forEach { (groupName, serviceList) ->
            result.add(GroupHeaderItem(groupName))
            result.addAll(serviceList)
        }
        result
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        if (services.isEmpty()) {
            EmptyHome(onOpenService = onOpenService)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(displayItems) { item ->
                    when (item) {
                        is GroupHeaderItem -> GroupHeader(name = item.name)
                        is InternalService -> ServiceCard(
                            service = item,
                            onClick = { onOpenService(item.url) },
                        )
                    }
                }
            }
        }
    }
}

// 用于 LazyColumn 区分标题项和服务项
private sealed class DisplayItem
private data class GroupHeaderItem(val name: String) : DisplayItem()

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GroupHeader(name: String) {
    Text(
        text = name,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, top = 4.dp),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ServiceCard(
    service: InternalService,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // emoji 图标
            Text(
                text = service.icon ?: "🔗",
                fontSize = 28.sp,
                modifier = Modifier.padding(end = 16.dp),
            )
            // 标题
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = service.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = service.url,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    // 简化：不使用 TextOverflow.Ellipsis，避免编译问题
                )
            }
            // 箭头 - 使用简单的文字代替图标
            Text(
                text = "›",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun EmptyHome(onOpenService: (String) -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("暂无内网服务", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "设置 → 内网服务清单 导入 YAML",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
