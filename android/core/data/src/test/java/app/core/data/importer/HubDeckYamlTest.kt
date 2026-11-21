package app.core.data.importer

import app.core.model.Category
import app.core.model.Endpoint
import app.core.model.EndpointScope
import app.core.model.HealthConfig
import app.core.model.HealthMode
import app.core.model.IconKind
import app.core.model.Service
import app.core.model.ServiceKind
import app.core.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `hubdeck.yaml` 的往返与容错单测（冻结契约 C-10）。
 */
class HubDeckYamlTest {

    @Test
    fun `export then parse is lossless`() {
        val services = listOf(
            Service(
                id = "8f1c",
                name = "Jellyfin",
                kind = ServiceKind.MEDIA,
                categoryId = "media",
                tagIds = listOf("t-ent", "t-family"),
                iconKind = IconKind.EMOJI,
                iconValue = "🎬",
                notes = "账号在密码管理器",
                pinned = true,
                sortIndex = 7,
                endpoints = listOf(
                    Endpoint(
                        id = "8f1c-lan",
                        serviceId = "8f1c",
                        scope = EndpointScope.LAN,
                        urlTemplate = "http://{host}:{port}",
                        host = "192.168.1.10",
                        port = 8096,
                        isPrimary = true,
                        note = "局域网",
                    ),
                    Endpoint(
                        id = "8f1c-tailnet",
                        serviceId = "8f1c",
                        scope = EndpointScope.TAILNET,
                        urlTemplate = "https://{host}",
                        host = "jellyfin.tailnet-xyz.ts.net",
                        port = null,
                        isPrimary = false,
                        note = null,
                    ),
                ),
                health = HealthConfig(mode = HealthMode.HTTP, path = "/health", timeoutMs = 2500),
                createdAt = 1_700_000_000_000,
                updatedAt = 1_700_000_100_000,
            ),
        )
        val categories = listOf(
            Category(id = "media", name = "影音", color = 0xFFF59E0BL, sortIndex = 2),
            Category(id = "tools", name = "工具", color = 0x8014B8A6L, sortIndex = 1),
        )
        val tags = listOf(Tag(id = "t-ent", name = "娱乐"), Tag(id = "t-family", name = "家庭"))

        val yaml = HubDeckYaml.export(
            services = services,
            magicDnsSuffix = "tailnet-xyz.ts.net",
            homeSsid = "MyHomeWiFi",
            categories = categories,
            tags = tags,
        )

        val catalog = HubDeckYaml.parseCatalog(yaml)

        assertEquals(services, catalog.services)
        assertEquals(categories, catalog.categories)
        assertEquals(tags, catalog.tags)
        assertEquals("MyHomeWiFi", catalog.settings.homeSsid)
        assertEquals("tailnet-xyz.ts.net", catalog.tailnet.magicDnsSuffix)
        assertEquals(1, catalog.services.size)
    }

    @Test
    fun `export keeps the plain parse entry point working`() {
        val service = Service(id = "a", name = "A", kind = ServiceKind.WEB)

        val yaml = HubDeckYaml.export(listOf(service))
        val parsed = HubDeckYaml.parse(yaml)

        assertEquals(listOf(service), parsed)
    }

    @Test
    fun `unknown enum values fall back to defaults`() {
        val yaml = """
            version: 1
            app: hubdeck
            services:
              - id: a
                name: A
                kind: 不存在的类型
                icon:
                  kind: 也不存在
                health:
                  mode: 玄学
                endpoints:
                  - scope: 量子
                    urlTemplate: "{host}"
                    host: example.local
        """.trimIndent()

        val service = HubDeckYaml.parse(yaml).single()

        assertEquals(ServiceKind.WEB, service.kind)
        assertEquals(IconKind.NONE, service.iconKind)
        assertEquals(HealthMode.NONE, service.health.mode)
        assertEquals(EndpointScope.LAN, service.endpoints.single().scope)
    }

    @Test
    fun `endpoint id falls back to service scope host when the file omits it`() {
        val yaml = """
            version: 1
            app: hubdeck
            services:
              - id: svc
                name: A
                endpoints:
                  - scope: lan
                    urlTemplate: "{host}"
                    host: example.local
        """.trimIndent()

        val endpoint = HubDeckYaml.parse(yaml).single().endpoints.single()

        assertEquals("svc-lan-example.local", endpoint.id)
    }

    @Test
    fun `export never contains credential material`() {
        val yaml = HubDeckYaml.export(listOf(Service(id = "a", name = "A")))

        assertTrue("凭据段必须恒为空", yaml.lines().any { it.trim() == "credentials: []" })
        assertFalse("不得出现凭据类型", yaml.contains("localapi_secret"))
        assertFalse("不得出现 token 类型", yaml.contains("oauth_token"))
        assertFalse("不得出现 api_token 类型", yaml.contains("api_token"))
    }

    @Test
    fun `parse ignores unknown fields so newer files still import`() {
        val yaml = """
            version: 1
            app: hubdeck
            somethingNew:
              nested: true
            services:
              - id: a
                name: A
                brandNewField: 42
        """.trimIndent()

        val service = HubDeckYaml.parse(yaml).single()

        assertEquals("a", service.id)
    }

    @Test
    fun `colour survives the round trip`() {
        assertEquals(0xFFF59E0BL, HubDeckYaml.parseColor("#F59E0B"))
        assertEquals(0x8014B8A6L, HubDeckYaml.parseColor("#8014B8A6"))
        assertEquals("#F59E0B", HubDeckYaml.formatColor(0xFFF59E0BL))
        assertEquals("#8014B8A6", HubDeckYaml.formatColor(0x8014B8A6L))
    }

    @Test
    fun `export writes the schema version and app marker`() {
        val yaml = HubDeckYaml.export(emptyList())

        assertTrue(yaml.contains("version: 1"))
        assertTrue(yaml.contains("app: hubdeck"))
        assertNotNull(HubDeckYaml.parse(yaml))
    }
}
