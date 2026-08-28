package core.gui.jewel.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.auth.AuthDetailsManager
import core.auth.AuthenticationMethod
import core.auth.MicrosoftAuthHandler
import core.auth.MicrosoftAuthServer
import core.config.Config
import core.gui.jewel.RealmInfo
import core.gui.jewel.portInUse
import core.gui.jewel.t
import org.apache.commons.lang3.SystemUtils

/**
 * ViewModel holding all settings state for the Compose GUI.
 * Reads from and writes to [Config].
 */
class SettingsViewModel {
    // ── Connection ─────────────────────────────────────────────
    var server by mutableStateOf("")
    var portLocal by mutableStateOf(25565)
    var portInUse by mutableStateOf(false)

    // ── Auth ───────────────────────────────────────────────────
    var authMethod by mutableStateOf(AuthenticationMethod.AUTOMATIC)
    var accessToken by mutableStateOf("")
    var msAuthLink by mutableStateOf("")
    var authResult by mutableStateOf("")
    var authFailed by mutableStateOf(false)
    var authStatus by mutableStateOf("")
    var authStatusFailed by mutableStateOf(false)

    // ── General ────────────────────────────────────────────────
    var extendedRenderDistance by mutableStateOf(0)
    var markUnsaved by mutableStateOf(true)
    var markOld by mutableStateOf(true)
    var renderOtherPlayers by mutableStateOf(false)
    var schematicMode by mutableStateOf(false)
    var enableInfoMessages by mutableStateOf(true)
    var drawExtendedChunks by mutableStateOf(false)

    // ── World ──────────────────────────────────────────────────
    var worldOutputDir by mutableStateOf("world")
    var levelSeed by mutableStateOf(0L)
    var centerX by mutableStateOf(0)
    var centerZ by mutableStateOf(0)
    var disableWorldGen by mutableStateOf(false)

    // ── Realms ─────────────────────────────────────────────────
    var realmsUsername by mutableStateOf("")
    var realms by mutableStateOf<List<RealmInfo>>(emptyList())
    var realmsLoading by mutableStateOf(false)

    // ── Error log ──────────────────────────────────────────────
    var errorMessages by mutableStateOf<List<String>>(emptyList())

    private var authServer: MicrosoftAuthServer? = null

    /**
     * Load all settings from the Config singleton into the ViewModel state.
     */
    fun loadFromConfig() {
        val c = Config.getInstance()
        server = c.server ?: ""
        portLocal = c.portLocal
        portInUse = portInUse(portLocal)

        authMethod = Config.getAuthMethod()
        accessToken = c.accessToken ?: ""
        validateAuth()

        extendedRenderDistance = c.extendedRenderDistance
        markUnsaved = !c.disableMarkUnsavedChunks
        markOld = c.markOldChunks
        renderOtherPlayers = c.renderOtherPlayers
        schematicMode = c.schematicMode
        enableInfoMessages = !c.disableInfoMessages
        drawExtendedChunks = c.drawExtendedChunks

        worldOutputDir = c.worldOutputDir ?: "world"
        levelSeed = c.levelSeed
        centerX = c.centerX
        centerZ = c.centerZ
        disableWorldGen = c.disableWorldGen

        realmsUsername = Config.getUsername() ?: ""
    }

    /**
     * Write all ViewModel state back to the Config singleton.
     */
    fun saveToConfig() {
        val c = Config.getInstance()
        c.server = server
        c.portLocal = kotlin.math.abs(portLocal)

        c.worldOutputDir = worldOutputDir
        c.centerX = centerX
        c.centerZ = centerZ
        c.levelSeed = levelSeed
        c.disableWorldGen = disableWorldGen

        c.extendedRenderDistance = kotlin.math.abs(extendedRenderDistance)
        c.disableMarkUnsavedChunks = !markUnsaved
        c.markOldChunks = markOld
        c.renderOtherPlayers = renderOtherPlayers
        c.disableInfoMessages = !enableInfoMessages
        c.drawExtendedChunks = drawExtendedChunks
        c.schematicMode = schematicMode

        c.username = realmsUsername

        Config.setAuthMethod(authMethod)

        if (accessToken.isNotEmpty()) {
            c.accessToken = accessToken
        }

        Config.save()
    }

    // ── Auth actions ───────────────────────────────────────────

    fun startMicrosoftAuth() {
        if (!SystemUtils.IS_OS_WINDOWS && !SystemUtils.IS_OS_MAC) {
            // On Linux, show the link for manual copy
            startMsAuthServer()
            return
        }
        startMsAuthServer()
    }

    private fun startMsAuthServer() {
        if (authServer != null) {
            msAuthLink = authServer!!.getShortUrl()
            return
        }

        try {
            authServer = MicrosoftAuthServer(
                { shortUrl ->
                    javax.swing.SwingUtilities.invokeLater {
                        msAuthLink = shortUrl
                    }
                    // Try to open in browser
                    try {
                        if (SystemUtils.IS_OS_WINDOWS || SystemUtils.IS_OS_MAC) {
                            java.awt.Desktop.getDesktop().browse(java.net.URI(shortUrl))
                        }
                    } catch (e: Exception) { /* ignore */ }
                },
                { authCode, usedPort ->
                    // fromCode is a network call — run on background thread
                    Thread {
                        try {
                            Config.setMicrosoftAuth(MicrosoftAuthHandler.fromCode(authCode, usedPort))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        javax.swing.SwingUtilities.invokeLater {
                            authServer = null
                            msAuthLink = ""
                            validateAuth()
                        }
                    }.start()
                },
            )
        } catch (e: Exception) {
            e.printStackTrace()
            javax.swing.SwingUtilities.invokeLater {
                authFailed = true
            }
        }
    }

    fun checkAuthStatus() {
        Thread {
            AuthDetailsManager.validateAuthStatus(
                { username ->
                    javax.swing.SwingUtilities.invokeLater {
                        authStatus = "Valid session found! Username: $username"
                        authStatusFailed = false
                    }
                },
                { msg ->
                    javax.swing.SwingUtilities.invokeLater {
                        authStatus = msg ?: "Not logged in"
                        authStatusFailed = true
                    }
                },
            )
        }.start()
    }

    private fun validateAuth() {
        Thread {
            AuthDetailsManager.validateAuthStatus(
                { username ->
                    javax.swing.SwingUtilities.invokeLater {
                        authResult = t("gui.auth.username", username)
                        authFailed = false
                    }
                },
                { _ ->
                    javax.swing.SwingUtilities.invokeLater {
                        authResult = t("gui.auth.not_logged_in")
                        authFailed = true
                    }
                },
            )
        }.start()
    }

    // ── Realms ─────────────────────────────────────────────────

    fun loadRealms() {
        if (realmsUsername.isBlank()) return
        realmsLoading = true
        realms = listOf(RealmInfo("Loading...", "", null, false))

        val api = core.auth.RealmsApiHandler(realmsUsername)
        api.requestRealms { str ->
            try {
                val gson = com.google.gson.Gson()
                val servers = gson.fromJson(str, RealmsResponse::class.java)
                if (servers?.servers == null || servers.servers.isEmpty()) {
                    realms = listOf(RealmInfo(
                        "No realms found for user $realmsUsername", "", null, false
                    ))
                } else {
                    realms = servers.servers.map { s ->
                        RealmInfo(s.name ?: "?", s.motd ?: "", null, false, id = s.id, api = api)
                    }
                }
            } catch (e: Exception) {
                realms = listOf(RealmInfo("Error: ${e.message}", "", null, false))
            }
            realmsLoading = false
        }
    }

    // ── World dir ──────────────────────────────────────────────

    fun openWorldDir() {
        try {
            val path = core.util.PathUtils.toPath(worldOutputDir)
            val file = path.toFile()
            val target = if (file.exists() && file.isDirectory) file
                else path.parent?.toFile()?.takeIf { it.exists() }
                ?: return
            java.awt.Desktop.getDesktop().open(target)
        } catch (e: Exception) { /* ignore */ }
    }
}

// ── JSON response classes for Realms API ──────────────────────────────────

private data class RealmsResponse(
    val servers: List<RealmServer> = emptyList(),
)

private data class RealmServer(
    val id: Int = 0,
    val name: String? = null,
    val motd: String? = null,
)
