package com.hubdeck.app.ui.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hubdeck.app.core.data.repo.ServiceRepository
import com.hubdeck.app.core.model.EndpointScope
import com.hubdeck.app.core.model.NetworkContext
import com.hubdeck.app.core.model.Service
import com.hubdeck.app.core.tailscale.EndpointResolver
import com.hubdeck.app.core.tailscale.EndpointUrlRenderer
import com.hubdeck.app.core.tailscale.TailnetRepository
import com.hubdeck.app.ui.ServiceOpener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServiceDetailViewModel @Inject constructor(
    private val repository: ServiceRepository,
    private val tailnetRepository: TailnetRepository,
) : ViewModel() {

    private val _service = MutableStateFlow<Service?>(null)
    val service: StateFlow<Service?> = _service

    private val _magicDnsSuffix = MutableStateFlow<String?>(null)
    val magicDnsSuffix: StateFlow<String?> = _magicDnsSuffix

    fun load(serviceId: String) {
        viewModelScope.launch {
            _service.value = repository.findService(serviceId)
            _magicDnsSuffix.value = tailnetRepository.refreshLocalState().magicDnsSuffix
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDetailScreen(
    serviceId: String,
    onBack: () -> Unit,
    viewModel: ServiceDetailViewModel = hiltViewModel(),
) {
    val service by viewModel.service.collectAsStateWithLifecycle()
    val magicDnsSuffix by viewModel.magicDnsSuffix.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedEndpointId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serviceId) { viewModel.load(serviceId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(service?.name ?: "服务") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val current = service
        if (current == null) {
            Text("加载中…", modifier = Modifier.padding(padding))
            return@Scaffold
        }

        val selected = current.endpoints.firstOrNull { it.id == selectedEndpointId }
            ?: EndpointResolver.resolve(current, NetworkContext.UNKNOWN).endpoint

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(
                    onClick = {
                        val url = selected?.let { EndpointUrlRenderer.render(it, magicDnsSuffix) }
                        if (url != null) ServiceOpener.open(context, url)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("打开服务")
                }
            }

            item { Text("访问地址", style = MaterialTheme.typography.titleMedium) }

            items(current.endpoints) { endpoint ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        RadioButton(
                            selected = endpoint.id == selected?.id,
                            onClick = { selectedEndpointId = endpoint.id },
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(scopeLabel(endpoint.scope))
                            Text(
                                text = EndpointUrlRenderer.render(endpoint, magicDnsSuffix),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (!current.notes.isNullOrBlank()) {
                item {
                    Text("备注", style = MaterialTheme.typography.titleMedium)
                    Text(current.notes.orEmpty())
                }
            }
        }
    }
}

private fun scopeLabel(scope: EndpointScope): String = when (scope) {
    EndpointScope.LAN -> "局域网地址"
    EndpointScope.TAILNET -> "Tailnet 地址"
    EndpointScope.PUBLIC -> "公网地址"
}
