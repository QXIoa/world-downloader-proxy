package core.gui.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.ui.component.Text

/**
 * Full-screen "update available" tab. Shown on startup when a newer
 * GitHub release is detected. The user can either open the release
 * page in a browser or dismiss the notification for this session.
 */
@Composable
fun UpdateScreen(
    info: UpdateChecker.UpdateInfo,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = t("gui.update.title"),
            color = Color(0xFF27CE40),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Font1,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = t("gui.update.version_available", info.latestVersion),
            color = Color(0xFFBBBBBB),
            fontSize = 14.sp,
            fontFamily = Font2,
        )

        Spacer(Modifier.height(24.dp))

        // Release notes (if present)
        if (info.releaseNotes.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2B2B2B))
                    .padding(16.dp),
            ) {
                Text(
                    text = info.releaseNotes,
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp,
                    fontFamily = Font2,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MinecraftButton(
                text = t("gui.update.download"),
                onClick = {
                    try {
                        java.awt.Desktop.getDesktop().browse(java.net.URI(info.releaseUrl))
                    } catch (e: Exception) { }
                },
            )
            MinecraftButton(
                text = t("gui.update.dismiss"),
                onClick = onDismiss,
            )
        }
    }
}
