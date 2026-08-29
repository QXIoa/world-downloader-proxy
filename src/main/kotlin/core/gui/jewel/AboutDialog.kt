package core.gui.jewel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import core.util.AppVersion
import org.jetbrains.jewel.ui.component.Text
import java.awt.Toolkit

@Composable
fun AboutDialog(
    iconPainter: Painter?,
    onClose: () -> Unit,
) {
    val javaVer = System.getProperty("java.version")

    val githubPainter: Painter? = remember {
        // NOTE: ImageIO.read() — synchronous on all platforms. Toolkit.getImage() is
        // asynchronous on Windows and crashes with Width/Height -1 (see Main.kt note).
        val url = Thread.currentThread().contextClassLoader.getResource("ui/icon/github.png")
        if (url != null) {
            try {
                BitmapPainter(javax.imageio.ImageIO.read(url).toComposeImageBitmap())
            } catch (e: Exception) { null }
        } else null
    }

    Dialog(
        onCloseRequest = onClose,
        title = "About",
        state = rememberDialogState(width = 380.dp, height = 320.dp),
        resizable = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (iconPainter != null) {
                Image(
                    painter = iconPainter,
                    contentDescription = "App icon",
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(16.dp))
            }

            Text(
                text = "World Downloader Proxy",
                color = Color(0xFF27CE40),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Font1,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Version: ${AppVersion.get()}",
                color = Color(0xFFBBBBBB),
                fontSize = 13.sp,
                fontFamily = Font2,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Java: $javaVer",
                color = Color(0xFF888888),
                fontSize = 12.sp,
                fontFamily = Font2,
            )

            Spacer(Modifier.height(16.dp))

            // GitHub link with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    try {
                        java.awt.Desktop.getDesktop().browse(java.net.URI("https://github.com/XInfiniterX/world-downloader-proxy"))
                    } catch (e: Exception) { }
                },
            ) {
                if (githubPainter != null) {
                    Image(
                        painter = githubPainter,
                        contentDescription = "GitHub",
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = "See this project on GitHub",
                    color = Color(0xFF6B9BFF),
                    fontSize = 13.sp,
                    fontFamily = Font2,
                    textDecoration = TextDecoration.Underline,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Based on minecraft-world-downloader",
                color = Color(0xFF666666),
                fontSize = 11.sp,
            )
            Text(
                text = "github.com/mircokroon/minecraft-world-downloader",
                color = Color(0xFF555555),
                fontSize = 10.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    try {
                        java.awt.Desktop.getDesktop().browse(java.net.URI("https://github.com/mircokroon/minecraft-world-downloader"))
                    } catch (e: Exception) { }
                },
            )
        }
    }
}
