package core.gui.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.auth.AuthenticationMethod
import core.config.Config
import core.gui.jewel.components.SettingsViewModel
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onStart: () -> Unit,
    onAbout: () -> Unit = {},
) {
    val config = Config.getInstance()
    val isStarted = config.isStarted

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        val tabs = listOf(
            t("gui.tab.connection"),
            t("gui.tab.general"),
            t("gui.tab.world"),
            t("gui.tab.authentication"),
            t("gui.tab.realms"),
            t("gui.tab.error_log"),
        )
        var selectedTab by remember { mutableStateOf(0) }

        TabBar(tabs = tabs, selected = selectedTab, onSelect = { selectedTab = it })

        Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF232323))
                .padding(16.dp),
        ) {
            when (selectedTab) {
                0 -> ConnectionTab(vm, isStarted)
                1 -> GeneralTab(vm, isStarted)
                2 -> WorldTab(vm, isStarted)
                3 -> AuthTab(vm)
                4 -> RealmsTab(vm, isStarted)
                5 -> ErrorLogTab(vm)
            }
        }

        Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())

        // Bottom bar
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "About",
                color = Color(0xFF888888),
                fontSize = 12.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable { onAbout() }
                    .padding(4.dp),
            )

            MinecraftButton(
                text = if (isStarted) t("gui.button.save") else t("gui.button.start"),
                onClick = onStart,
                accent = true,
                enabled = vm.server.isNotBlank() && !vm.portInUse,
            )
        }
    }
}

// ── Connection tab ────────────────────────────────────────────────────────

@Composable
private fun ConnectionTab(vm: SettingsViewModel, isStarted: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard {
            SettingTextField(
                label = t("gui.settings.server_address"),
                value = vm.server,
                onValueChange = { vm.server = it },
                helpText = t("gui.settings.server_address_tooltip"),
                enabled = !isStarted,
            )

            LabeledRow(label = t("gui.settings.authentication")) {
                MinecraftButton(
                    text = t("gui.settings.microsoft_login"),
                    onClick = { vm.startMicrosoftAuth() },
                    enabled = !isStarted,
                    modifier = Modifier.widthIn(min = 160.dp),
                )
            }

            if (vm.authResult.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(text = vm.authResult, isError = vm.authFailed)
                }
            }
        }

        if (isStarted) {
            val port = Config.getInstance().portLocal
            Text(
                text = t("gui.connection.hint", port),
                color = Color(0xFF6BCB7E),
                fontSize = 13.sp,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

// ── General tab ───────────────────────────────────────────────────────────

@Composable
private fun GeneralTab(vm: SettingsViewModel, isStarted: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard {
            LabeledRow(
                label = t("gui.settings.extended_render_distance"),
                helpText = t("gui.settings.extended_render_distance_tooltip"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${vm.extendedRenderDistance}", color = Color(0xFFDDDDDD), fontSize = 13.sp,
                         modifier = Modifier.width(30.dp))
                    McSlider(
                        value = vm.extendedRenderDistance.toFloat(),
                        onValueChange = { v -> vm.extendedRenderDistance = v.toInt() },
                        valueRange = 0f..32f,
                        modifier = Modifier.width(200.dp),
                    )
                }
            }

            Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))

            SettingCheckbox(t("gui.settings.mark_unsaved"), vm.markUnsaved, { vm.markUnsaved = it }, t("gui.settings.mark_unsaved_tooltip"))
            SettingCheckbox(t("gui.settings.grey_out_old"), vm.markOld, { vm.markOld = it }, t("gui.settings.grey_out_old_tooltip"))
            SettingCheckbox(t("gui.settings.show_players"), vm.renderOtherPlayers, { vm.renderOtherPlayers = it }, t("gui.settings.show_players_tooltip"))
            SettingCheckbox(t("gui.settings.schematic_mode"), vm.schematicMode, { vm.schematicMode = it }, t("gui.settings.schematic_mode_tooltip"))
        }

        SectionCard(title = t("gui.settings.advanced")) {
            SettingCheckbox(t("gui.settings.send_info_messages"), vm.enableInfoMessages, { vm.enableInfoMessages = it }, t("gui.settings.send_info_messages_tooltip"))
            SettingCheckbox(t("gui.settings.draw_extended_chunks"), vm.drawExtendedChunks, { vm.drawExtendedChunks = it }, t("gui.settings.draw_extended_chunks_tooltip"))
        }

        SectionCard {
            IntField(
                label = t("gui.settings.downloader_port"),
                value = vm.portLocal,
                onValueChange = {
                    vm.portLocal = it
                    vm.portInUse = portInUse(it)
                },
                helpText = t("gui.settings.downloader_port_tooltip"),
                enabled = !isStarted,
            )
            if (vm.portInUse) {
                Text(
                    text = t("gui.port.in_use"),
                    color = Color(0xFFE07070),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 188.dp, top = 2.dp),
                )
            }
        }
    }
}

// ── World tab ─────────────────────────────────────────────────────────────

@Composable
private fun WorldTab(vm: SettingsViewModel, isStarted: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard {
            SettingTextField(
                label = t("gui.settings.world_output"),
                value = vm.worldOutputDir,
                onValueChange = { vm.worldOutputDir = it },
                enabled = !isStarted,
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.padding(start = 180.dp), verticalAlignment = Alignment.CenterVertically) {
                MinecraftButton(
                    text = t("gui.settings.open"),
                    onClick = { vm.openWorldDir() },
                    enabled = !isStarted,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))

            LongField(t("gui.settings.level_seed"), vm.levelSeed, { vm.levelSeed = it }, enabled = !isStarted)

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("gui.settings.offset"), color = Color(0xFFBBBBBB), fontSize = 13.sp, fontFamily = Font2, modifier = Modifier.width(180.dp))
                IntField("", vm.centerX, { vm.centerX = it }, enabled = !isStarted, width = 80, fillWidth = false)
                Spacer(Modifier.width(16.dp))
                IntField("", vm.centerZ, { vm.centerZ = it }, enabled = !isStarted, width = 80, fillWidth = false)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(180.dp))
                Text("X", color = Color(0xFF888888), fontSize = 11.sp, fontFamily = Font2, modifier = Modifier.width(80.dp).padding(start = 4.dp))
                Spacer(Modifier.width(16.dp))
                Text("Z", color = Color(0xFF888888), fontSize = 11.sp, fontFamily = Font2, modifier = Modifier.width(80.dp).padding(start = 4.dp))
            }

            SettingCheckbox(t("gui.settings.prevent_chunk_generation"), vm.disableWorldGen, { vm.disableWorldGen = it })
        }
    }
}

// ── Authentication tab ────────────────────────────────────────────────────

@Composable
private fun AuthTab(vm: SettingsViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard {
            Text(t("gui.auth_tab.method"), color = Color(0xFFBBBBBB), fontSize = 13.sp, fontFamily = Font2, modifier = Modifier.padding(bottom = 8.dp))

            AuthenticationMethod.values().forEach { method ->
                CustomRadioButton(
                    label = method.getLabel(),
                    selected = vm.authMethod == method,
                    onClick = { vm.authMethod = method },
                )
            }

            Spacer(Modifier.height(12.dp))

            when (vm.authMethod) {
                AuthenticationMethod.AUTOMATIC -> {
                    Text("Authentication will be handled automatically.", color = Color(0xFF888888), fontSize = 12.sp, fontFamily = Font2)
                }
                AuthenticationMethod.MICROSOFT -> {
                    MinecraftButton(
                        text = t("gui.settings.microsoft_login"),
                        onClick = { vm.startMicrosoftAuth() },
                        accent = true,
                        modifier = Modifier.widthIn(min = 200.dp),
                    )
                    if (vm.msAuthLink.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(t("gui.auth_tab.copy_link"), color = Color(0xFFBBBBBB), fontSize = 12.sp, fontFamily = Font2)
                        Text(
                            text = vm.msAuthLink,
                            color = Color(0xFF6B9BFF),
                            fontSize = 12.sp,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                clipboard.setContents(java.awt.datatransfer.StringSelection(vm.msAuthLink), null)
                            }.padding(4.dp),
                        )
                    }
                }
                AuthenticationMethod.MANUAL -> {
                    SettingTextField(
                        label = t("gui.auth_tab.access_token"),
                        value = vm.accessToken,
                        onValueChange = { token ->
                            vm.accessToken = token.filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' }
                        },
                    )
                    Text(
                        text = t("gui.auth_tab.more_info"),
                        color = Color(0xFF6B9BFF),
                        fontSize = 12.sp,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            try {
                                java.awt.Desktop.getDesktop().browse(java.net.URI(
                                    "https://github.com/mircokroon/minecraft-world-downloader/wiki/Authentication"
                                ))
                            } catch (e: Exception) { }
                        }.padding(4.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            MinecraftButton(
                text = t("gui.auth_tab.check_status"),
                onClick = { vm.checkAuthStatus() },
                modifier = Modifier.widthIn(min = 200.dp),
            )

            if (vm.authStatus.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                StatusBadge(text = vm.authStatus, isError = vm.authStatusFailed)
            }

            if (vm.authFailed) {
                Spacer(Modifier.height(8.dp))
                Text(t("gui.auth_tab.failed"), color = Color(0xFFE07070), fontSize = 12.sp, fontFamily = Font2)
            }
        }
    }
}

// ── Realms tab ────────────────────────────────────────────────────────────

@Composable
private fun RealmsTab(vm: SettingsViewModel, isStarted: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard {
            SettingTextField(
                label = t("gui.realms.username"),
                value = vm.realmsUsername,
                onValueChange = { vm.realmsUsername = it },
                enabled = !isStarted,
            )

            MinecraftButton(
                text = t("gui.realms.load"),
                onClick = { vm.loadRealms() },
                enabled = vm.realmsUsername.isNotBlank() && !isStarted,
                modifier = Modifier.padding(start = 188.dp),
            )

            Spacer(Modifier.height(12.dp))

            if (vm.realms.isNotEmpty()) {
                Text("Realms:", color = Color(0xFFBBBBBB), fontSize = 13.sp, fontFamily = Font2, modifier = Modifier.padding(bottom = 4.dp))
                vm.realms.forEach { realm ->
                    RealmItemRow(realm, onSelect = { address -> vm.server = address })
                    Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
                }
            } else if (vm.realmsLoading) {
                Text("Loading...", color = Color(0xFF888888), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun RealmItemRow(realm: RealmInfo, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(realm.name, color = Color(0xFFDDDDDD), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (realm.motd.isNotEmpty()) {
                Text(realm.motd, color = Color(0xFF888888), fontSize = 11.sp)
            }
        }
        val addr = realm.address
        if (addr != null) {
            MinecraftButton(text = t("gui.realm.select"), onClick = { onSelect(addr) }, accent = true, modifier = Modifier.widthIn(min = 80.dp))
        } else if (realm.loading) {
            Text("...", color = Color(0xFF888888), fontSize = 12.sp)
        } else {
            MinecraftButton(text = t("gui.realm.request_ip"), onClick = { realm.requestIp() }, modifier = Modifier.widthIn(min = 100.dp))
        }
    }
}

// ── Error log tab ─────────────────────────────────────────────────────────

@Composable
private fun ErrorLogTab(vm: SettingsViewModel) {
    val allErrors = remember(vm.errorMessages, vm.backendErrors) {
        vm.errorMessages + (vm.backendErrors?.toList() ?: emptyList())
    }
    if (allErrors.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No errors", color = Color(0xFF666666), fontSize = 14.sp)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            allErrors.forEach { msg ->
                Text(msg, color = Color(0xFFE07070), fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}
