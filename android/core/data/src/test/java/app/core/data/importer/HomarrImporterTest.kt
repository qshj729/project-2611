package app.core.data.importer

import app.core.model.EndpointScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomarrImporterTest {

    private val yaml = """
        ---
        groups:
          - id: media
            name: 影音
        items:
          - name: Jellyfin
            url: http://192.168.1.10:8096
            description: 家庭影院
            icon: /api/icons/jellyfin.png
            tags:
              - 娱乐
          - name: Broken
            url: ftp://old-server
    """.trimIndent()

    @Test
    fun `imports homarr items and skips non http urls`() {
        val result = HomarrImporter.parse(yaml)
        assertEquals(1, result.services.size)
        assertEquals("Jellyfin", result.services.first().name)
        assertEquals(EndpointScope.LAN, result.services.first().endpoints.first().scope)
        assertEquals("192.168.1.10", result.services.first().endpoints.first().host)
        assertEquals(8096, result.services.first().endpoints.first().port)
        assertTrue(result.warnings.any { it.contains("ftp") })
        assertTrue(result.warnings.any { it.contains("第") && it.contains("行") })
    }

    @Test
    fun `splits urls into template host and port`() {
        assertEquals("192.168.1.10", HomarrImporter.hostOf("http://192.168.1.10:8096"))
        assertEquals(8096, HomarrImporter.portOf("http://192.168.1.10:8096"))
        assertEquals("{host}/admin", HomarrImporter.templateOf("http://192.168.1.10:8096/admin"))
    }

    @Test
    fun `reports line numbers in warnings`() {
        val yamlWithLineBreaks = """
            groups: []
            items:
              - name: Item1
                url: http://example.com
              - name: Item2
                url: ftp://bad
              - name: Item3
                url: http://good.com
        """.trimIndent()

        val result = HomarrImporter.parse(yamlWithLineBreaks)
        assertEquals(2, result.services.size)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.first().contains("行"))
    }
}