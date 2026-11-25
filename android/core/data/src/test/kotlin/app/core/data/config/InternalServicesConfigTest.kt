package app.core.data.config

import app.core.model.InternalService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YAML 内网服务清单解析与渲染单测。
 *
 * 覆盖（G-9 验收口径）：
 * 1. 合法清单 → 解析成功，字段正确
 * 2. 非法协议（ftp:// / javascript: 等）→ 拒绝，有明确错误
 * 3. 空标题 → 拒绝，原因明确
 * 4. 分组 → 多分组 / 无分组的解析与顺序
 * 5. 往返（parse → export → parse）→ 结果等价
 */
class InternalServicesConfigTest {

    /** 1. 合法清单 → 解析成功，字段正确 */
    @Test
    fun `valid manifest parses all fields correctly`() {
        val yaml = """
            version: 1
            services:
              - title: Jellyfin
                url: https://jellyfin.tailnet-xyz.ts.net
                icon: "🎬"
                group: 媒体
                note: 家庭媒体服务器
              - title: Grafana
                url: http://grafana:3000
                icon: "📊"
                group: 监控
              - title: 无分组服务
                url: https://standalone.example.ts.net
        """.trimIndent()

        val result = InternalServicesConfig.parse(yaml)

        assertEquals(0, result.rejected.size)
        assertEquals(3, result.services.size)

        // 第 1 条：全字段
        assertEquals("Jellyfin", result.services[0].title)
        assertEquals("https://jellyfin.tailnet-xyz.ts.net", result.services[0].url)
        assertEquals("🎬", result.services[0].icon)
        assertEquals("媒体", result.services[0].group)
        assertEquals("家庭媒体服务器", result.services[0].note)

        // 第 2 条：无 note
        assertEquals("Grafana", result.services[1].title)
        assertEquals("http://grafana:3000", result.services[1].url)
        assertEquals("📊", result.services[1].icon)
        assertEquals("监控", result.services[1].group)
        assertEquals(null, result.services[1].note)

        // 第 3 条：仅 title + url（icon/group/note 为 null）
        assertEquals("无分组服务", result.services[2].title)
        assertEquals("https://standalone.example.ts.net", result.services[2].url)
        assertEquals(null, result.services[2].icon)
        assertEquals(null, result.services[2].group)
        assertEquals(null, result.services[2].note)
    }

    /** 2. 非法协议 → 拒绝，原因明确 */
    @Test
    fun `invalid protocols are rejected with clear reason`() {
        val yaml = """
            version: 1
            services:
              - title: FTP 服务
                url: ftp://files.example.com
              - title: JS 注入
                url: javascript:alert(1)
              - title: file 协议
                url: file:///etc/passwd
              - title: intent 协议
                url: intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end
              - title: 无 scheme
                url: example.com
              - title: 合法 https（对照组）
                url: https://valid.example.ts.net
        """.trimIndent()

        val result = InternalServicesConfig.parse(yaml)

        // 5 条被拒绝
        assertEquals(5, result.rejected.size)
        // 1 条通过
        assertEquals(1, result.services.size)
        assertEquals("合法 https（对照组）", result.services[0].title)

        // 验证每条拒绝理由包含明确错误文案
        result.rejected.forEach { rejected ->
            assertTrue("拒绝理由应包含协议限制提示: ${rejected.reason}", rejected.reason.contains("http"))
        }

        // 具体检查几条
        val ftpRejected = result.rejected.first { it.title == "FTP 服务" }
        assertEquals("ftp://files.example.com", ftpRejected.url)
        assertTrue(ftpRejected.reason.contains("http"))

        val jsRejected = result.rejected.first { it.title == "JS 注入" }
        assertEquals("javascript:alert(1)", jsRejected.url)
        assertTrue(jsRejected.reason.contains("http"))
    }

    /** 3. 空标题 → 拒绝，原因明确 */
    @Test
    fun `empty title is rejected with clear reason`() {
        val yaml = """
            version: 1
            services:
              - title: ""
                url: https://valid.example.ts.net
              - title: "   "
                url: https://also-valid.example.ts.net
              - title: 正常标题
                url: https://normal.example.ts.net
        """.trimIndent()

        val result = InternalServicesConfig.parse(yaml)

        assertEquals(2, result.rejected.size)
        assertEquals(1, result.services.size)
        assertEquals("正常标题", result.services[0].title)

        result.rejected.forEach { rejected ->
            assertTrue("拒绝理由应提示标题为空: ${rejected.reason}", rejected.reason.contains("标题为空"))
        }
    }

    /** 4. 分组 → 多分组 / 无分组的解析与顺序 */
    @Test
    fun `groups are parsed and order preserved`() {
        val yaml = """
            version: 1
            services:
              - title: 服务 A
                url: https://a.example.ts.net
                group: 生产
              - title: 服务 B
                url: https://b.example.ts.net
              - title: 服务 C
                url: https://c.example.ts.net
                group: 测试
              - title: 服务 D
                url: https://d.example.ts.net
                group: 生产
              - title: 服务 E
                url: https://e.example.ts.net
        """.trimIndent()

        val result = InternalServicesConfig.parse(yaml)

        assertEquals(0, result.rejected.size)
        assertEquals(5, result.services.size)

        // 顺序保持 YAML 顺序
        assertEquals("服务 A", result.services[0].title)
        assertEquals("生产", result.services[0].group)

        assertEquals("服务 B", result.services[1].title)
        assertEquals(null, result.services[1].group) // 无分组 → null（上层渲染时归入「未分组」）

        assertEquals("服务 C", result.services[2].title)
        assertEquals("测试", result.services[2].group)

        assertEquals("服务 D", result.services[3].title)
        assertEquals("生产", result.services[3].group)

        assertEquals("服务 E", result.services[4].title)
        assertEquals(null, result.services[4].group)
    }

    /** 5. 往返（parse -> export -> parse）-> 结果等价 */
    @Test
    fun `roundTrip parse export parse preserves equivalence`() {
        val originalYaml = """
            version: 1
            services:
              - title: Jellyfin
                url: https://jellyfin.tailnet-xyz.ts.net
                icon: "🎬"
                group: 媒体
                note: 家庭媒体服务器
              - title: Grafana
                url: http://grafana:3000
                icon: "📊"
                group: 监控
              - title: 无分组服务
                url: https://standalone.example.ts.net
        """.trimIndent()

        // 第一次解析
        val firstResult = InternalServicesConfig.parse(originalYaml)
        assertEquals(0, firstResult.rejected.size)

        // 导出为 YAML
        val exportedYaml = InternalServicesConfig.render(firstResult.services)

        // 第二次解析导出的 YAML
        val secondResult = InternalServicesConfig.parse(exportedYaml)
        assertEquals(0, secondResult.rejected.size)

        // 两次解析结果等价（字段逐个对比）
        assertEquals(firstResult.services.size, secondResult.services.size)
        for (i in firstResult.services.indices) {
            val a = firstResult.services[i]
            val b = secondResult.services[i]
            assertEquals("title at $i", a.title, b.title)
            assertEquals("url at $i", a.url, b.url)
            assertEquals("icon at $i", a.icon, b.icon)
            assertEquals("group at $i", a.group, b.group)
            assertEquals("note at $i", a.note, b.note)
        }
    }

    /** 额外：空白 YAML → 空列表，不崩溃 */
    @Test
    fun `empty yaml returns empty list`() {
        val result = InternalServicesConfig.parse("version: 1\nservices: []")
        assertEquals(0, result.services.size)
        assertEquals(0, result.rejected.size)
    }

    /** 额外：只有被拒绝条目的 YAML → accepted 为空 */
    @Test
    fun `yaml with only rejected entries returns empty accepted list`() {
        val yaml = """
            version: 1
            services:
              - title: ""
                url: https://example.com
              - title: Bad
                url: ftp://bad.com
        """.trimIndent()

        val result = InternalServicesConfig.parse(yaml)
        assertEquals(0, result.services.size)
        assertEquals(2, result.rejected.size)
    }
}