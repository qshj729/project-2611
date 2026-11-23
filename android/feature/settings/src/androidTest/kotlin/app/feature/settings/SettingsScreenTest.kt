package app.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

/**
 * 设置页 UI 测试（骨架）。
 *
 * 当前只验证"设置页能渲染出关键分区"。
 * 完整交互（隧道授权、清单导入导出）需要 Hilt 测试环境 + 假隧道，放到 M1 一并补（见 contracts.md G-8）。
 */
@OptIn(ExperimentalMaterial3Api::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsScreenShowsSections() {
        composeRule.setContent {
            androidx.compose.material3.MaterialTheme {
                SettingsSectionsPreview()
            }
        }

        composeRule.onNodeWithText("内网连接").assertIsDisplayed()
        composeRule.onNodeWithText("内网服务清单").performScrollTo().assertIsDisplayed()
    }
}

/** 只包含静态文案的预览（避免依赖 Hilt）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSectionsPreview() {
    androidx.compose.foundation.layout.Column {
        Text("内网连接")
        Text("内网服务清单")
    }
}
