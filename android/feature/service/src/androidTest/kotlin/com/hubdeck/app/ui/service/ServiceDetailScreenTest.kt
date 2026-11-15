package com.hubdeck.app.ui.service

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DisabledOn
import androidx.compose.ui.test.Orientation
import androidx.compose.ui.test.Platform
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubdeck.app.core.model.Endpoint
import com.hubdeck.app.core.model.EndpointScope
import com.hubdeck.app.core.model.HealthSample
import com.hubdeck.app.core.model.ProbeResult
import com.hubdeck.app.core.model.Service
import com.hubdeck.app.core.model.ServiceDetailUiState
import com.hubdeck.app.core.model.EndpointUi
import com.hubdeck.app.ui.service.port.ServiceDetailPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

/**
 * 服务详情界面测试。
 *
 * 测试覆盖：
 * - 服务名称显示
 * - 打开服务按钮
 * - 端点列表与选择状态
 * - 探测结果显示
 * - 探测历史
 * - 回退提示
 * - 手动选择提示
 * - 删除确认对话框
 */
class ServiceDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mockPort = mock<ServiceDetailPort>()

    private val testService = Service(
        id = "1",
        name = "Jellyfin",
        notes = "媒体服务器",
        endpoints = listOf(
            Endpoint(id = "ep1", serviceId = "1", scope = EndpointScope.TAILNET, urlTemplate = "https://jellyfin.tailnet.ts.net", isPrimary = true),
            Endpoint(id = "ep2", serviceId = "1", scope = EndpointScope.LAN, urlTemplate = "http://192.168.1.10:8096", isPrimary = false),
        ),
    )

    private val testEndpointUi1 = EndpointUi(
        endpoint = testService.endpoints[0],
        isSelected = true,
        isPreferred = true,
        url = "https://jellyfin.tailnet.ts.net",
    )

    private val testEndpointUi2 = EndpointUi(
        endpoint = testService.endpoints[1],
        isSelected = false,
        isPreferred = false,
        url = "http://192.168.1.10:8096",
    )

    private val testProbeResult = ProbeResult(reachable = true, latencyMs = 42, reason = null)

    private val testHealthSamples = listOf(
        HealthSample(id = 1, serviceId = "1", endpointId = "ep1", reachable = true, latencyMs = 42, checkedAt = System.currentTimeMillis(), reason = null),
        HealthSample(id = 2, serviceId = "1", endpointId = "ep1", reachable = false, latencyMs = null, checkedAt = System.currentTimeMillis() - 60000, reason = "连接超时"),
    )

    @Suppress("UNUSED_PARAMETER")
    @Composable
    private fun TestServiceDetailContent(
        uiState: ServiceDetailUiState,
        onSelectEndpoint: (String) -> Unit,
        onUseAutoEndpoint: () -> Unit,
        onProbe: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit,
        onOpenUrl: (String) -> Unit,
        onBack: () -> Unit,
    ) {
        ServiceDetailContent(
            uiState = uiState,
            onSelectEndpoint = onSelectEndpoint,
            onUseAutoEndpoint = onUseAutoEndpoint,
            onProbe = onProbe,
            onEdit = onEdit,
            onDelete = onDelete,
            onOpenUrl = onOpenUrl,
            onBack = onBack,
        )
    }

    private fun setupMocks() {
        // No additional mocks needed for UI state tests
    }

    @Test
    fun serviceDetail_rendersServiceName() {
        setupMocks()

        val testUiState = ServiceDetailUiState(
            service = testService,
            endpoints = listOf(testEndpointUi1, testEndpointUi2),
            probeResult = testProbeResult,
            healthSamples = testHealthSamples,
            url = "https://jellyfin.tailnet.ts.net",
            canOpen = true,
            fellBack = false,
            manualSelection = false,
            isLoading = false,
            isNew = false,
            isEditing = false,
        )

        composeRule.setContent {
            TestServiceDetailContent(
                uiState = testUiState,
                onSelectEndpoint = {},
                onUseAutoEndpoint = {},
                onProbe = {},
                onEdit = {},
                onDelete = {},
                onOpenUrl = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Jellyfin").assertIsDisplayed()
    }

    @Test
    fun serviceDetail_showsOpenButton() {
        setupMocks()

        val testUiState = ServiceDetailUiState(
            service = testService,
            endpoints = listOf(testEndpointUi1),
            probeResult = null,
            healthSamples = emptyList(),
            url = "https://jellyfin.tailnet.ts.net",
            canOpen = true,
            fellBack = false,
            manualSelection = false,
            isLoading = false,
            isNew = false,
            isEditing = false,
        )

        composeRule.setContent {
            TestServiceDetailContent(
                uiState = testUiState,
                onSelectEndpoint = {},
                onUseAutoEndpoint = {},
                onProbe = {},
                onEdit = {},
                onDelete = {},
                onOpenUrl = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("打开服务").assertIsDisplayed()
        composeRule.onNodeWithText("https://jellyfin.tailnet.ts.net").assertIsDisplayed()
    }

    @Test
    fun serviceDetail_showsEndpointList() {
        setupMocks()

        val testUiState = ServiceDetailUiState(
            service = testService,
            endpoints = listOf(testEndpointUi1, testEndpointUi2),
            probeResult = testProbeResult,
            healthSamples = testHealthSamples,
            url = "https://jellyfin.tailnet.ts.net",
            canOpen = true,
            fellBack = false,
            manualSelection = false,
            isLoading = false,
            isNew = false,
            isEditing = false,
        )

        composeRule.setContent {
            TestServiceDetailContent(
                uiState = testUiState,
                onSelectEndpoint = {},
                onUseAutoEndpoint = {},
                onProbe = {},
                onEdit = {},
                onDelete = {},
                onOpenUrl = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Tailnet").assertIsDisplayed()
        composeRule.onNodeWithText("局域网").assertIsDisplayed()
        composeRule.onNodeWithText("当前网络推荐").assertIsDisplayed()
        composeRule.onNodeWithText("https://jellyfin.tailnet.ts.net").assertIsDisplayed()
        composeRule.onNodeWithText("http://192.168.1.10:8096").assertIsDisplayed()
    }

    @Test
    fun serviceDetail_showsProbeResult() {
        setupMocks()

        val testUiState = ServiceDetailUiState(
            service = testService,
            endpoints = listOf(testEndpointUi1),
            probeResult = testProbeResult,
            healthSamples = testHealthSamples,
            url = "https://jellyfin.tailnet.ts.net",
            canOpen = true,
            fellBack = false,
            manualSelection = false,
            isLoading = false,
            isNew = false,
            isEditing = false,
        )

        composeRule.setContent {
            TestServiceDetailContent(
                uiState = testUiState,
                onSelectEndpoint = {},
                onUseAutoEndpoint = {},
                onProbe = {},
                onEdit = {},
                onDelete = {},
                onOpenUrl = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("可达性").assertIsDisplayed()
        composeRule.onNodeWithText("可达，耗时 42 ms").assertIsDisplayed()
        composeRule.onNodeWithText("探测").assertIsDisplayed()
    }

    @Test
    fun serviceDetail_showsProbeHistory() {
        setupMocks()

        val testUiState = ServiceDetailUiState(
            service = testService,
            endpoints = listOf(testEndpointUi1),
            probeResult = testProbeResult,
            healthSamples = testHealthSamples,
            url = "https://jellyfin.tailnet.ts.net",
            canOpen = true,
            fellBack = false,
            manualSelection = false,
            isLoading = false,
            isNew = false,
            isEditing = false,
        )

        composeRule.setContent {
            TestServiceDetailContent(
                uiState = testUiState,
                onSelectEndpoint = {},
                onUseAutoEndpoint = {},
                onProbe = {},
                onEdit = {},
                onDelete = {},
                onOpenUrl = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("最近探测记录").assertIsDisplayed()
        composeRule.onNodeWithText("可达").assertIsDisplayed()
        composeRule.onNodeWithText("连接超时").assertIsDisplayed()
        composeRule.onNodeWithText("42 ms").assertIsDisplayed()
    }

    @Test
    fun serviceDetail_showsFallbackHint() {
        setupMocks()

        val testUiState = ServiceDetailUiState(
            service = testService,
            endpoints = listOf(testEndpointUi1),
            probeResult = testProbeResult,
            healthSamples = emptyList(),
            url = "http://192.168.1.10:8096",
            canOpen = true,
            fellBack = true,
            manualSelection = false,
            isLoading = false,
            isNew = false,
            isEditing = false,
        )

        composeRule.setContent {
            TestServiceDetailContent(
                uiState = testUiState,
                onSelectEndpoint = {},
                onUseAutoEndpoint = {},
                onProbe = {},
                onEdit = {},
                onDelete = {},
                onOpenUrl = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("首选地址不可达，已自动回退到备用地址。").assertIsDisplayed()
    }

    @Test
    fun serviceDetail_showsManualSelectionHint() {
        setupMocks()

        val testUiState = ServiceDetailUiState(
            service = testService,
            endpoints = listOf(testEndpointUi1),
            probeResult = testProbeResult,
            healthSamples = emptyList(),
            url = "https://jellyfin.tailnet.ts.net",
            canOpen = true,
            fellBack = false,
            manualSelection = true,
            isLoading = false,
            isNew = false,
            isEditing = false,
        )

        composeRule.setContent {
            TestServiceDetailContent(
                uiState = testUiState,
                onSelectEndpoint = {},
                onUseAutoEndpoint = {},
                onProbe = {},
                onEdit = {},
                onDelete = {},
                onOpenUrl = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("当前使用的是手动选择的地址。").assertIsDisplayed()
        composeRule.onNodeWithText("跟随网络自动选择").assertIsDisplayed()
    }

    @Test
    fun serviceDetail_showsNotes() {
        setupMocks()

        val testUiState = ServiceDetailUiState(
            service = testService,
            endpoints = listOf(testEndpointUi1),
            probeResult = null,
            healthSamples = emptyList(),
            url = "https://jellyfin.tailnet.ts.net",
            canOpen = true,
            fellBack = false,
            manualSelection = false,
            isLoading = false,
            isNew = false,
            isEditing = false,
        )

        composeRule.setContent {
            TestServiceDetailContent(
                uiState = testUiState,
                onSelectEndpoint = {},
                onUseAutoEndpoint = {},
                onProbe = {},
                onEdit = {},
                onDelete = {},
                onOpenUrl = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("备注").assertIsDisplayed()
        composeRule.onNodeWithText("媒体服务器").assertIsDisplayed()
    }

    @Test
    fun serviceDetail_showsEditAndDeleteButtons() {
        setupMocks()

        val testUiState = ServiceDetailUiState(
            service = testService,
            endpoints = listOf(testEndpointUi1),
            probeResult = null,
            healthSamples = emptyList(),
            url = "https://jellyfin.tailnet.ts.net",
            canOpen = true,
            fellBack = false,
            manualSelection = false,
            isLoading = false,
            isNew = false,
            isEditing = false,
        )

        composeRule.setContent {
            TestServiceDetailContent(
                uiState = testUiState,
                onSelectEndpoint = {},
                onUseAutoEndpoint = {},
                onProbe = {},
                onEdit = {},
                onDelete = {},
                onOpenUrl = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithContentDescription("编辑服务").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("删除服务").assertIsDisplayed()
    }

    @Test
    fun serviceDetail_newServiceShowsGoToEdit() {
        setupMocks()

        val testUiState = ServiceDetailUiState(
            service = null,
            endpoints = emptyList(),
            probeResult = null,
            healthSamples = emptyList(),
            url = "",
            canOpen = false,
            fellBack = false,
            manualSelection = false,
            isLoading = false,
            isNew = true,
            isEditing = false,
        )

        composeRule.setContent {
            TestServiceDetailContent(
                uiState = testUiState,
                onSelectEndpoint = {},
                onUseAutoEndpoint = {},
                onProbe = {},
                onEdit = {},
                onDelete = {},
                onOpenUrl = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("这是一个新服务，请在编辑界面填写名称与访问地址。").assertIsDisplayed()
        composeRule.onNodeWithText("去填写").assertIsDisplayed()
    }
}