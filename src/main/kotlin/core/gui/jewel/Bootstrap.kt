package core.gui.jewel

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import core.config.Config
import core.messages.Messages
import core.sniffer.VersionRegistry
import core.util.LocalDateTimeAdapter
import core.util.PathUtils
import java.io.FileReader

// ── i18n helper ──────────────────────────────────────────────────────────

fun t(key: String, vararg args: Any?): String = Messages.gui(key, *args)

// ── Config bootstrap (without JavaFX) ────────────────────────────────────

/**
 * Bootstraps the backend Config without launching JavaFX.
 * Replicates what Launcher.main() + Config.init() do, but skips the
 * GuiManager.loadSceneSettings() call that would start JavaFX.
 */
fun bootstrapConfig() {
    // 0. Fix working directory — same as Launcher.fixCwd() so world/cache
    //    output lands next to the jar (or user.dir) instead of the "abc" default.
    try {
        PathUtils.fixCwd()
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // 1. Discover version module via SPI (same as Launcher)
    val module = VersionRegistry.getInstance().getModule(0)
        ?: throw IllegalStateException(
            "No VersionModule found on classpath. Ensure a version module is present."
        )
    Config.setVersionModule(module)

    // 2. Set config path via reflection (Config.init would set this but also launch JavaFX)
    val configPath = PathUtils.toPath("cache", "config.json")
    val field = Config::class.java.getDeclaredField("configPath")
    field.isAccessible = true
    field.set(null, configPath)

    // 3. Load config from file (same as Config.createConfig)
    val config = if (configPath.toFile().exists() && configPath.toFile().isFile) {
        try {
            GsonBuilder()
                .registerTypeAdapter(java.time.LocalDateTime::class.java, LocalDateTimeAdapter())
                .create()
                .fromJson<Config>(
                    JsonReader(FileReader(configPath.toFile())),
                    Config::class.java,
                )
        } catch (e: Exception) {
            println("Could not read config: ${e.message}")
            null
        }
    } else {
        null
    } ?: Config()

    Config.setInstance(config)
}

/**
 * Starts the proxy after the user clicks "Start" in the Compose GUI.
 * Prevents JavaFX from launching by disabling settings GUI mode and
 * the JavaFX map scene.
 */
fun startProxyFromCompose() {
    val config = Config.getInstance()

    // Prevent JavaFX settings window from launching
    Config.disableSettingsGui()

    // Prevent JavaFX map window from launching — Compose map bridge handles it
    config.disableGui = true

    // Save and start
    Config.save()
    config.settingsComplete()
}

/**
 * Saves settings without starting the proxy (when already running).
 */
fun saveSettingsOnly() {
    Config.save()
}

/**
 * Shuts down the proxy and saves world data.
 */
fun shutdownProxy() {
    try {
        val module = Config.getVersionModule()
        module.getWorldManager().shutdown()
        module.getWorldManager().save()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Checks if a local port is in use.
 */
fun portInUse(port: Int): Boolean {
    return try {
        java.net.ServerSocket(port).use { }
        false
    } catch (e: java.io.IOException) {
        true
    }
}
