package app.feature.browser

import app.core.model.TunnelState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import org.junit.Test
import org.junit.Assert.*

/**
 * [tunnelStateToUi] 纯函数的单元测试。
 * 验证每个 TunnelState 都能映射到正确的 UI 属性（文案、图标、可点击性等）。
 * 颜色由 [tunnelStateToColors] 在 Composable 上下文中解析，此处不测试颜色。
 */
class TunnelStatusUiTest {

    @Test
    fun `Connected 状态映射正确`() {
        val state = TunnelState.Connected(
            nodeName = "my-device",
            tailnetIps = listOf("100.1.2.3"),
            magicDnsSuffix = "tailnet-xyz.ts.net",
        )
        val ui = tunnelStateToUi(state)

        assertTrue("文案包含节点名", ui.text.contains("my-device"))
        assertEquals("图标为 Wifi", Icons.Default.Wifi, ui.icon)
        assertFalse("不可点击", ui.isClickable)
        assertFalse("无箭头", ui.showChevron)
    }

    @Test
    fun `Starting 状态映射正确`() {
        val state = TunnelState.Starting
        val ui = tunnelStateToUi(state)

        assertEquals("文案为连接中", "正在连接内网…", ui.text)
        assertEquals("图标为 HourglassEmpty", Icons.Default.HourglassEmpty, ui.icon)
        assertFalse("不可点击", ui.isClickable)
        assertFalse("无箭头", ui.showChevron)
    }

    @Test
    fun `Authorizing 状态映射正确`() {
        val state = TunnelState.Authorizing(loginUrl = "https://login.tailscale.com/abc123")
        val ui = tunnelStateToUi(state)

        assertEquals("文案为需授权", "需授权才能连接内网", ui.text)
        assertEquals("图标为 Help", Icons.Default.Help, ui.icon)
        assertTrue("可点击", ui.isClickable)
        assertTrue("显示箭头", ui.showChevron)
    }

    @Test
    fun `Error 状态映射正确`() {
        val state = TunnelState.Error(reason = "Authentication expired")
        val ui = tunnelStateToUi(state)

        assertTrue("文案包含错误原因", ui.text.contains("Authentication expired"))
        assertEquals("图标为 Error", Icons.Default.Error, ui.icon)
        assertTrue("可点击（可重试授权）", ui.isClickable)
        assertTrue("显示箭头", ui.showChevron)
    }

    @Test
    fun `Disconnected 状态映射正确`() {
        val state = TunnelState.Disconnected
        val ui = tunnelStateToUi(state)

        assertEquals("文案为空（不显示状态条）", "", ui.text)
        assertEquals("图标为 WifiOff", Icons.Default.WifiOff, ui.icon)
        assertFalse("不可点击", ui.isClickable)
        assertFalse("无箭头", ui.showChevron)
    }

    @Test
    fun `所有状态互不相同_文案唯一`() {
        val states = listOf(
            TunnelState.Connected("a", listOf(), null),
            TunnelState.Starting,
            TunnelState.Authorizing("url"),
            TunnelState.Error("err"),
            TunnelState.Disconnected,
        )
        val texts = states.map { tunnelStateToUi(it).text }.distinct()
        assertEquals("每个状态的文案唯一", states.size, texts.size)
    }

    @Test
    fun `所有状态互不相同_图标唯一`() {
        val states = listOf(
            TunnelState.Connected("a", listOf(), null),
            TunnelState.Starting,
            TunnelState.Authorizing("url"),
            TunnelState.Error("err"),
            TunnelState.Disconnected,
        )
        val icons = states.map { tunnelStateToUi(it).icon }.distinct()
        assertEquals("每个状态的图标唯一", states.size, icons.size)
    }

    @Test
    fun `Authorizing 和 Error 可点击_其余不可点击`() {
        val clickableStates = listOf(
            TunnelState.Authorizing("url"),
            TunnelState.Error("err"),
        )
        val nonClickableStates = listOf(
            TunnelState.Connected("a", listOf(), null),
            TunnelState.Starting,
            TunnelState.Disconnected,
        )

        clickableStates.forEach { state ->
            val ui = tunnelStateToUi(state)
            assertTrue("${state} 应该可点击", ui.isClickable)
        }
        nonClickableStates.forEach { state ->
            val ui = tunnelStateToUi(state)
            assertFalse("${state} 不应可点击", ui.isClickable)
        }
    }
}