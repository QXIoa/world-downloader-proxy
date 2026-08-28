package core.gui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "World Downloader — Compose"
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                var clicks by remember { mutableStateOf(0) }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Compose GUI działa!")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Kliknięć: $clicks")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { clicks++ }) {
                        Text("Kliknij")
                    }
                }
            }
        }
    }
}
