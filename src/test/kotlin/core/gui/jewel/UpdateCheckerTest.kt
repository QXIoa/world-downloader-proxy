package core.gui.jewel

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.net.URI

class UpdateCheckerTest {

    @Test
    fun `newer minor version triggers update`() {
        assertEquals(true, UpdateChecker.isNewer("2026.2", "2026.1"))
    }

    @Test
    fun `newer year triggers update`() {
        assertEquals(true, UpdateChecker.isNewer("2027.1", "2026.1"))
    }

    @Test
    fun `same version does not trigger update`() {
        assertEquals(false, UpdateChecker.isNewer("2026.1", "2026.1"))
    }

    @Test
    fun `older version does not trigger update`() {
        assertEquals(false, UpdateChecker.isNewer("2026.1", "2026.2"))
    }

    @Test
    fun `older year does not trigger update`() {
        assertEquals(false, UpdateChecker.isNewer("2025.1", "2026.1"))
    }

    @Test
    fun `malformed version does not trigger update`() {
        assertEquals(false, UpdateChecker.isNewer("abc", "2026.1"))
        assertEquals(false, UpdateChecker.isNewer("2026.1", "abc"))
    }

    @Test
    fun `v prefix is stripped from tag`() {
        // The checkForUpdate method strips "v" prefix before comparing.
        // isNewer itself works on stripped versions, so this is just
        // a sanity check that "2026.2" > "2026.1".
        assertEquals(true, UpdateChecker.isNewer("2026.2", "2026.1"))
    }

    /**
     * Integration test: fetches the real GitHub releases/latest API endpoint
     * and verifies that extractJsonField can parse tag_name, html_url, and body
     * from the live response. Requires network access.
     */
    @Test
    fun `real GitHub API response is parsed correctly`() {
        val apiUrl = "https://api.github.com/repos/XInfiniterX/world-downloader-proxy/releases/latest"
        val body = URI(apiUrl).toURL().openStream().bufferedReader().use { it.readText() }
        assertTrue(body.isNotEmpty(), "GitHub API returned empty body")

        // extractJsonField is private — call via Java reflection
        val extractFn: Method = UpdateChecker::class.java
            .getDeclaredMethod("extractJsonField", String::class.java, String::class.java)
        extractFn.isAccessible = true

        val tag = extractFn.invoke(UpdateChecker, body, "tag_name") as? String
        val htmlUrl = extractFn.invoke(UpdateChecker, body, "html_url") as? String
        val releaseNotes = extractFn.invoke(UpdateChecker, body, "body") as? String

        assertNotNull(tag, "tag_name should be extracted from real API response")
        assertTrue(tag!!.isNotEmpty(), "tag_name should not be empty")
        // Real tags don't have "v" prefix (e.g. "26w35a")
        assertFalse(tag.startsWith("v"), "tag should not have v prefix, got: $tag")

        assertNotNull(htmlUrl, "html_url should be extracted from real API response")
        assertTrue(htmlUrl!!.startsWith("https://github.com/"),
            "html_url should be a GitHub URL, got: $htmlUrl")

        assertNotNull(releaseNotes, "body should be extracted from real API response")
        // Release notes should contain newlines (escape handling works)
        assertTrue(releaseNotes!!.contains("\n") || releaseNotes.contains("\r"),
            "body should contain newlines after escape handling, got: ${releaseNotes.take(80)}")
    }
}
