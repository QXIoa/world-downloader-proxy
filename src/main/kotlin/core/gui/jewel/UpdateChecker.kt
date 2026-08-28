package core.gui.jewel

import core.util.AppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * Checks GitHub releases for a newer version than the current [AppVersion].
 *
 * Version format: YYYY.M (e.g. "2026.1", "2027.3"). GitHub release tags are
 * expected to be prefixed with "v" (e.g. "v2026.1").
 *
 * Snapshot builds ("snapshot") never trigger update notifications.
 */
object UpdateChecker {

    /**
     * Reads the repo URL from `/repo.txt` (filtered at build time from
     * `build.gradle.kts`). Falls back to a hardcoded URL if the resource
     * is missing. Change the URL in `build.gradle.kts` if the repo
     * owner/name changes.
     */
    private val repoUrl: String by lazy {
        try {
            AppVersion::class.java.getResourceAsStream("/repo.txt")?.bufferedReader()?.use {
                it.readLine()?.trim()
            } ?: "https://github.com/XInfiniterX/world-downloader-proxy"
        } catch (e: Exception) {
            "https://github.com/XInfiniterX/world-downloader-proxy"
        }
    }

    private val releasesApiUrl: String by lazy {
        // Convert github.com/OWNER/REPO → api.github.com/repos/OWNER/REPO/releases/latest
        "https://api.github.com/repos/" +
            repoUrl.removePrefix("https://github.com/").removeSuffix("/") +
            "/releases/latest"
    }

    data class UpdateInfo(
        val latestVersion: String,
        val releaseUrl: String,
        val releaseNotes: String,
    )

    /**
     * Returns an [UpdateInfo] if a newer version is available, or null if:
     * - current version is "snapshot"
     * - current version is already the latest
     * - the check fails for any reason
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val current = AppVersion.get()
        // Snapshot builds skip update checks entirely.
        if (current == "snapshot") return@withContext null

        try {
            val body = URI(releasesApiUrl).toURL().openStream().bufferedReader().use { it.readText() }
            if (body.isEmpty()) return@withContext null

            val tag = extractJsonField(body, "tag_name") ?: return@withContext null
            val htmlUrl = extractJsonField(body, "html_url") ?: return@withContext null
            val body_field = extractJsonField(body, "body") ?: ""

            val latest = tag.removePrefix("v").trim()
            if (isNewer(latest, current)) {
                UpdateInfo(
                    latestVersion = latest,
                    releaseUrl = htmlUrl,
                    releaseNotes = body_field,
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns true if [latest] is strictly newer than [current].
     * Both are expected in "YYYY.M" format. Non-numeric or malformed
     * versions are treated as equal (no update).
     */
    fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest) ?: return false
        val c = parseVersion(current) ?: return false
        if (l.first != c.first) return l.first > c.first
        return l.second > c.second
    }

    private fun parseVersion(v: String): Pair<Int, Int>? {
        val parts = v.split(".")
        if (parts.size < 2) return null
        val year = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        return year to minor
    }

    /**
     * Naive JSON field extraction — avoids pulling in a JSON parser
     * dependency for a single API call. Handles simple string fields
     * and the releases/latest shape.
     */
    private fun extractJsonField(json: String, field: String): String? {
        val key = "\"$field\":"
        val idx = json.indexOf(key)
        if (idx < 0) return null
        val after = idx + key.length
        // Skip whitespace
        var i = after
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length) return null
        val c = json[i]
        if (c == '"') {
            // String value — read until unescaped closing quote
            val sb = StringBuilder()
            i++
            while (i < json.length) {
                val ch = json[i]
                if (ch == '\\' && i + 1 < json.length) {
                    val esc = json[i + 1]
                    sb.append(when (esc) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'b' -> '\b'
                        'f' -> '\u000C'
                        '"' -> '"'
                        '\\' -> '\\'
                        '/' -> '/'
                        else -> esc
                    })
                    i += 2
                    continue
                }
                if (ch == '"') break
                sb.append(ch)
                i++
            }
            return sb.toString()
        }
        // Non-string value — read until comma or brace
        val sb = StringBuilder()
        while (i < json.length && json[i] != ',' && json[i] != '}' && json[i] != '\n') {
            sb.append(json[i])
            i++
        }
        return sb.trim().toString().ifEmpty { null }
    }
}
