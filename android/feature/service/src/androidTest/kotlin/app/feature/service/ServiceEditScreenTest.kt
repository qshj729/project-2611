package app.feature.service

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.core.model.EndpointDraft
import app.core.model.EndpointScope
import app.core.model.HealthMode
import app.core.model.ServiceEditUiState
import app.core.model.Category
import app.feature.service.port.ServiceEditPort
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
 * 新建/编辑服务界面测试。
 *
 * 测试覆盖：
 * - 标题显示（新建/编辑）
 * - 服务名称输入
 * - 分类选择
 * - 收藏开关
 * - 备注输入
 * - 端点编辑（主机名、端口、URL 模板、scope、首选）
 * - 添加/移除端点
 * - 生成 Tailnet 地址
 * - 健康检查配置（模式、路径、超时）
 * - 保存/取消按钮
 */
class ServiceEditScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mockPort = mock<ServiceEditPort>()

    private val testCategories = listOf(
        Category(id = "media", name = "影音", color = 0xFFF59E0B, sortIndex = 0),
        Category(id = "monitoring", name = "监控", color = 0xFF3B82F6, sortIndex = 1),
    )

    private val testEndpointDraft = EndpointDraft(
        id = "ep1",
        host = "jellyfin",
        port = "8096",
        urlTemplate = "https://{host}:{port}",
        scope = EndpointScope.TAILNET,
        isPrimary = true,
    )

    @Suppress("UNUSED_PARAMETER")
    @Composable
    private fun TestServiceEditContent(
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
    ) {
        ServiceEditContent(
            uiState = uiState,
            onNameChange = onNameChange,
            onNotesChange = onNotesChange,
            onCategorySelected = onCategorySelected,
            onPinnedChange = onPinnedChange,
            onHealthModeChange = onHealthModeChange,
            onHealthPathChange = onHealthPathChange,
            onHealthTimeoutChange = onHealthTimeoutChange,
            onEndpointHostChange = onEndpointHostChange,
            onEndpointPortChange = onEndpointPortChange,
            onEndpointTemplateChange = onEndpointTemplateChange,
            onEndpointScopeChange = onEndpointScopeChange,
            onEndpointPrimaryChange = onEndpointPrimaryChange,
            onAddEndpoint = onAddEndpoint,
            onRemoveEndpoint = onRemoveEndpoint,
            onGenerateTailnetEndpoint = onGenerateTailnetEndpoint,
            onSave = onSave,
            onCancel = onCancel,
            onBack = onBack,
        )
    }

    private fun setupMocks() {
        // No additional mocks needed
    }

    @Test
    fun serviceEdit_newServiceShowsCorrectTitle() {
        setupMocks()

        val testUiState = ServiceEditUiState(
            isNew = true,
            name = "",
            categories = testCategories,
            categoryId = null,
            pinned = false,
            notes = "",
            endpoints = listOf(testEndpointDraft),
            magicDnsSuffix = "tailnet.ts.net",
            healthMode = HealthMode.TCP,
            healthPath = "/",
            healthTimeoutMs = 3000,
            canSave = false,
            isSaving = false,
            error = null,
        )

        composeRule.setContent {
            TestServiceEditContent(
                uiState = testUiState,
                onNameChange = {},
                onNotesChange = {},
                onCategorySelected = {},
                onPinnedChange = {},
                onHealthModeChange = {},
                onHealthPathChange = {},
                onHealthTimeoutChange = {},
                onEndpointHostChange = {},
                onEndpointPortChange = {},
                onEndpointTemplateChange = {},
                onEndpointScopeChange = {},
                onEndpointPrimaryChange = {},
                onAddEndpoint = {},
                onRemoveEndpoint = {},
                onGenerateTailnetEndpoint = {},
                onSave = {},
                onCancel = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("新建服务").assertIsDisplayed()
    }

    @Test
    fun serviceEdit_existingServiceShowsCorrectTitle() {
        setupMocks()

        val testUiState = ServiceEditUiState(
            isNew = false,
            name = "Jellyfin",
            categories = testCategories,
            categoryId = "media",
            pinned = true,
            notes = "媒体服务器",
            endpoints = listOf(testEndpointDraft),
            magicDnsSuffix = "tailnet.ts.net",
            healthMode = HealthMode.TCP,
            healthPath = "/",
            healthTimeoutMs = 3000,
            canSave = true,
            isSaving = false,
            error = null,
        )

        composeRule.setContent {
            TestServiceEditContent(
                uiState = testUiState,
                onNameChange = {},
                onNotesChange = {},
                onCategorySelected = {},
                onPinnedChange = {},
                onHealthModeChange = {},
                onHealthPathChange = {},
                onHealthTimeoutChange = {},
                onEndpointHostChange = {},
                onEndpointPortChange = {},
                onEndpointTemplateChange = {},
                onEndpointScopeChange = {},
                onEndpointPrimaryChange = {},
                onAddEndpoint = {},
                onRemoveEndpoint = {},
                onGenerateTailnetEndpoint = {},
                onSave = {},
                onCancel = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("编辑服务").assertIsDisplayed()
    }

    @Test
    fun serviceEdit_showsNameField() {
        setupMocks()

        val testUiState = ServiceEditUiState(
            isNew = true,
            name = "Jellyfin",
            categories = testCategories,
            categoryId = null,
            pinned = false,
            notes = "",
            endpoints = listOf(testEndpointDraft),
            magicDnsSuffix = "tailnet.ts.net",
            healthMode = HealthMode.TCP,
            healthPath = "/",
            healthTimeoutMs = 3000,
            canSave = true,
            isSaving = false,
            error = null,
        )

        composeRule.setContent {
            TestServiceEditContent(
                uiState = testUiState,
                onNameChange = {},
                onNotesChange = {},
                onCategorySelected = {},
                onPinnedChange = {},
                onHealthModeChange = {},
                onHealthPathChange = {},
                onHealthTimeoutChange = {},
                onEndpointHostChange = {},
                onEndpointPortChange = {},
                onEndpointTemplateChange = {},
                onEndpointScopeChange = {},
                onEndpointPrimaryChange = {},
                onAddEndpoint = {},
                onRemoveEndpoint = {},
                onGenerateTailnetEndpoint = {},
                onSave = {},
                onCancel = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("服务名称").assertIsDisplayed()
        composeRule.onNodeWithText("Jellyfin").assertIsDisplayed()
    }

    @Test
    fun serviceEdit_showsCategoryChips() {
        setupMocks()

        val testUiState = ServiceEditUiState(
            isNew = true,
            name = "",
            categories = testCategories,
            categoryId = "media",
            pinned = false,
            notes = "",
            endpoints = listOf(testEndpointDraft),
            magicDnsSuffix = "tailnet.ts.net",
            healthMode = HealthMode.TCP,
            healthPath = "/",
            healthTimeoutMs = 3000,
            canSave = true,
            isSaving = false,
            error = null,
        )

        composeRule.setContent {
            TestServiceEditContent(
                uiState = testUiState,
                onNameChange = {},
                onNotesChange = {},
                onCategorySelected = {},
                onPinnedChange = {},
                onHealthModeChange = {},
                onHealthPathChange = {},
                onHealthTimeoutChange = {},
                onEndpointHostChange = {},
                onEndpointPortChange = {},
                onEndpointTemplateChange = {},
                onEndpointScopeChange = {},
                onEndpointPrimaryChange = {},
                onAddEndpoint = {},
                onRemoveEndpoint = {},
                onGenerateTailnetEndpoint = {},
                onSave = {},
                onCancel = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("分类").assertIsDisplayed()
        composeRule.onNodeWithText("影音").assertIsDisplayed()
        composeRule.onNodeWithText("监控").assertIsDisplayed()
    }

    @Test
    fun serviceEdit_showsPinnedSwitch() {
        setupMocks()

        val testUiState = ServiceEditUiState(
            isNew = true,
            name = "",
            categories = testCategories,
            categoryId = null,
            pinned = true,
            notes = "",
            endpoints = listOf(testEndpointDraft),
            magicDnsSuffix = "tailnet.ts.net",
            healthMode = HealthMode.TCP,
            healthPath = "/",
            healthTimeoutMs = 3000,
            canSave = true,
            isSaving = false,
            error = null,
        )

        composeRule.setContent {
            TestServiceEditContent(
                uiState = testUiState,
                onNameChange = {},
                onNotesChange = {},
                onCategorySelected = {},
                onPinnedChange = {},
                onHealthModeChange = {},
                onHealthPathChange = {},
                onHealthTimeoutChange = {},
                onEndpointHostChange = {},
                onEndpointPortChange = {},
                onEndpointTemplateChange = {},
                onEndpointScopeChange = {},
                onEndpointPrimaryChange = {},
                onAddEndpoint = {},
                onRemoveEndpoint = {},
                onGenerateTailnetEndpoint = {},
                onSave = {},
                onCancel = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("收藏置顶").assertIsDisplayed()
    }

    @Test
    fun serviceEdit_showsNotesField() {
        setupMocks()

        val testUiState = ServiceEditUiState(
            isNew = true,
            name = "",
            categories = testCategories,
            categoryId = null,
            pinned = false,
            notes = "媒体服务器",
            endpoints = listOf(testEndpointDraft),
            magicDnsSuffix = "tailnet.ts.net",
            healthMode = HealthMode.TCP,
            healthPath = "/",
            healthTimeoutMs = 3000,
            canSave = true,
            isSaving = false,
            error = null,
        )

        composeRule.setContent {
            TestServiceEditContent(
                uiState = testUiState,
                onNameChange = {},
                onNotesChange = {},
                onCategorySelected = {},
                onPinnedChange = {},
                onHealthModeChange = {},
                onHealthPathChange = {},
                onHealthTimeoutChange = {},
                onEndpointHostChange = {},
                onEndpointPortChange = {},
                onEndpointTemplateChange = {},
                onEndpointScopeChange = {},
                onEndpointPrimaryChange = {},
                onAddEndpoint = {},
                onRemoveEndpoint = {},
                onGenerateTailnetEndpoint = {},
                onSave = {},
                onCancel = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("备注（可选）").assertIsDisplayed()
        composeRule.onNodeWithText("媒体服务器").assertIsDisplayed()
    }

    @Test
    fun serviceEdit_showsEndpointFields() {
        setupMocks()

        val testUiState = ServiceEditUiState(
            isNew = true,
            name = "",
            categories = testCategories,
            categoryId = null,
            pinned = false,
            notes = "",
            endpoints = listOf(testEndpointDraft),
            magicDnsSuffix = "tailnet.ts.net",
            healthMode = HealthMode.TCP,
            healthPath = "/",
            healthTimeoutMs = 3000,
            canSave = true,
            isSaving = false,
            error = null,
        )

        composeRule.setContent {
            TestServiceEditContent(
                uiState = testUiState,
                onNameChange = {},
                onNotesChange = {},
                onCategorySelected = {},
                onPinnedChange = {},
                onHealthModeChange = {},
                onHealthPathChange = {},
                onHealthTimeoutChange = {},
                onEndpointHostChange = {},
                onEndpointPortChange = {},
                onEndpointTemplateChange = {},
                onEndpointScopeChange = {},
                onEndpointPrimaryChange = {},
                onAddEndpoint = {},
                onRemoveEndpoint = {},
                onGenerateTailnetEndpoint = {},
                onSave = {},
                onCancel = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("访问地址").assertIsDisplayed()
        composeRule.onNodeWithText("主机名").assertIsDisplayed()
        composeRule.onNodeWithText("jellyfin").assertIsDisplayed()
        composeRule.onNodeWithText("端口（可选）").assertIsDisplayed()
        composeRule.onNodeWithText("8096").assertIsDisplayed()
        composeRule.onNodeWithText("URL 模板").assertIsDisplayed()
        composeRule.onNodeWithText("预览：https://jellyfin.tailnet.ts.net:8096").assertIsDisplayed()
    }

    @Test
    fun serviceEdit_showsScopeChips() {
        setupMocks()

        val testUiState = ServiceEditUiState(
            isNew = true,
            name = "",
            categories = testCategories,
            categoryId = null,
            pinned = false,
            notes = "",
            endpoints = listOf(testEndpointDraft),
            magicDnsSuffix = "tailnet.ts.net",
            healthMode = HealthMode.TCP,
            healthPath = "/",
            healthTimeoutMs = 3000,
            canSave = true,
            isSaving = false,
            error = null,
        )

        composeRule.setContent {
            TestServiceEditContent(
                uiState = testUiState,
                onNameChange = {},
                onNotesChange = {},
                onCategorySelected = {},
                onPinnedChange = {},
                onHealthModeChange = {},
                onHealthPathChange = {},
                onHealthTimeoutChange = {},
                onEndpointHostChange = {},
                onEndpointPortChange = {},
                onEndpointTemplateChange = {},
                onEndpointScopeChange = {},
                onEndpointPrimaryChange = {},
                onAddEndpoint = {},
                onRemoveEndpoint = {},
                onGenerateTailnetEndpoint = {},
                onSave = {},
                onCancel = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("局域网").assertIsDisplayed()
        composeRule.onNodeWithText("Tailnet").assertIsDisplayed()
        composeRule.onNodeWithText("公网").assertIsDisplayed()
    }

    @Test
    fun serviceEdit_showsPrimarySwitch() {
        setupMocks()

        val testUiState = ServiceEditUiState(
            isNew = true,
            name = "",
            categories = testCategories,
            categoryId = null,
            pinned = false,
            notes = "",
            endpoints = listOf(testEndpointDraft),
            magicDnsSuffix = "tailnet.ts.net",
            healthMode = HealthMode.TCP,
            healthPath = "/",
            healthTimeoutMs = 3000,
            canSave = true,
            isSaving = false,
            error = null,
        )

        composeRule.setContent {
            TestServiceEditContent(
                uiState = testUiState,
                onNameChange = {},
                onNotesChange = {},
                onCategorySelected = {},
                onPinnedChange = {},
                onHealthModeChange = {},
                onHealthPathChange = {},
                onHealthTimeoutChange = {},
                onEndpointHostChange = {},
                onEndpointPortChange = {},
                onEndpointTemplateChange = {},
                onEndpointScopeChange = {},
                onEndpointPrimaryChange = {},
                onAddEndpoint = {},
                onRemoveEndpoint = {},
                onGenerateTailnetEndpoint = {},
                onSave = {},
                onCancel = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("同网络类型内的首选地址").assertIsDisplayed()
    }

    @Test
    fun serviceEdit_showsAddEndpointButtons() {
        setupMocks()

        val testUiState = ServiceEditUiState(
            isNew = true,
            name = "",
            categories = testCategories,
            categoryId = null,
            pinned = false,
            notes = "",
            endpoints = listOf(testEndpointDraft),
            magicDnsSuffix = "tailnet.ts.net",
            healthMode = HealthMode.TCP,
            healthPath = "/",
            healthTimeoutMs = 3000,
            canSave = true,
            isSaving = false,
            error = null,
        )

        composeRule.setContent {
            TestServiceEditContent(
                uiState = testUiState,
                onNameChange = {},
                onNotesChange = {},
                onCategorySelected = {},
                onPinnedChange = {},
                onHealthModeChange = {},
                onHealthPathChange = {},
                onHealthTimeoutChange = {},
                onEndpointHostChange = {},
                onEndpointPortChange = {},
                onEndpointTemplateChange = {},
                onEndpointScopeChange = {},
                onEndpointPrimaryChange = {},
                onAddEndpoint = {},
                onRemoveEndpoint = {},
                onGenerateTailnetEndpoint = {},
                onSave = {},
                onCancel = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("添加地址").assertIsDisplayed()
        composeRule.onNodeWithText("生成 Tailnet 地址").assertIsDisplayed()
        composeRule.onNodeWithText("当前 MagicDNS 后缀：tailnet.ts.net").assertIsDisplayed()
    }

    @Test
    fun serviceEdit_showsHealthModeChips() {
        setupMocks()

        val testUiState = ServiceEditUiState(
            isNew = true,
            name = "",
            categories = testCategories,
            categoryId = null,
            pinned = false,
            notes = "",
            endpoints = listOf(testEndpointDraft),
            magicDnsSuffix = "tailnet.ts.net",
            healthMode = HealthMode.TCP,
            healthPath = "/",
            healthTimeoutMs = 3000,
            canSave = true,
            isSaving = false,
            error = null,
        )

        composeRule.setContent {
            TestServiceEditContent(
                uiState = testUiState,
                onNameChange = {},
                onNotesChange = {},
                onCategorySelected = {},
                onPinnedChange = {},
                onHealthModeChange = {},
                onHealthPathChange = {},
                onHealthTimeoutChange = {},
                onEndpointHostChange = {},
                onEndpointPortChange = {},
                onEndpointTemplateChange = {},
                onEndpointScopeChange = {},
                onEndpointPrimaryChange = {},
                onAddEndpoint = {},
                onRemoveEndpoint = {},
                onGenerateTailnetEndpoint = {},
                onSave = {},
                onCancel = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("健康检查").assertIsDisplayed()
        composeRule.onNodeWithText("不探测").assertIsDisplayed()
        composeRule.onNodeWithText("TCP").assertIsDisplayed()
        composeRule.onNodeWithText("HTTP").assertIsDisplayed()
    }

    @Test
    fun serviceEdit_showsHealthConfigWhenNotNone() {
        setupMocks()

        val testUiState = ServiceEditUiState(
            isNew = true,
            name = "",
            categories = testCategories,
            categoryId = null,
            pinned = false,
            notes = "",
            endpoints = listOf(testEndpointDraft),
            magicDnsSuffix = "tailnet.ts.net",
            healthMode = HealthMode.TCP,
            healthPath = "/health",
            healthTimeoutMs = 5000,
            canSave = true,
            isSaving = false,
            error = null,
        )

        composeRule.setContent {
            TestServiceEditContent(
                uiState = testUiState,
                onNameChange = {},
                onNotesChange = {},
                onCategorySelected = {},
                onPinnedChange = {},
                onHealthModeChange = {},
                onHealthPathChange = {},
                onHealthTimeoutChange = {},
                onEndpointHostChange = {},
                onEndpointPortChange = {},
                onEndpointTemplateChange = {},
                onEndpointScopeChange = {},
                onEndpointPrimaryChange = {},
                onAddEndpoint = {},
                onRemoveEndpoint = {},
                onGenerateTailnetEndpoint = {},
                onSave = {},
                onCancel = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("探测路径").assertIsDisplayed()
        composeRule.onNodeWithText("/health").assertIsDisplayed()
        composeRule.onNodeWithText("超时（毫秒）").assertIsDisplayed()
        composeRule.onNodeWithText("5000").assertIsDisplayed()
    }

    @Test
    fun serviceEdit_showsSaveAndCancelButtons() {
        setupMocks()

        val testUiState = ServiceEditUiState(
            isNew = true,
            name = "",
            categories = testCategories,
            categoryId = null,
            pinned = false,
            notes = "",
            endpoints = listOf(testEndpointDraft),
            magicDnsSuffix = "tailnet.ts.net",
            healthMode = HealthMode.TCP,
            healthPath = "/",
            healthTimeoutMs = 3000,
            canSave = true,
            isSaving = false,
            error = null,
        )

        composeRule.setContent {
            TestServiceEditContent(
                uiState = testUiState,
                onNameChange = {},
                onNotesChange = {},
                onCategorySelected = {},
                onPinnedChange = {},
                onHealthModeChange = {},
                onHealthPathChange = {},
                onHealthTimeoutChange = {},
                onEndpointHostChange = {},
                onEndpointPortChange = {},
                onEndpointTemplateChange = {},
                onEndpointScopeChange = {},
                onEndpointPrimaryChange = {},
                onAddEndpoint = {},
                onRemoveEndpoint = {},
                onGenerateTailnetEndpoint = {},
                onSave = {},
                onCancel = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("保存").assertIsDisplayed()
        composeRule.onNodeWithText("放弃修改").assertIsDisplayed()
    }
}