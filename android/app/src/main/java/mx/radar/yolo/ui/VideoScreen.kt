package mx.radar.yolo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import mx.radar.yolo.RadarViewModel

@Composable
fun VideoScreen(vm: RadarViewModel) {
    val cfg = vm.config.collectAsState().value
    val v = vm.video.collectAsState().value
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.procesarVideo(uri)
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Button(onClick = { elegir.launch("video/*") }, enabled = !v.procesando) { Text("Elegir video de la galería") }
        if (v.frame != null || v.procesando) {
            LinearProgressIndicator(progress = { v.progreso }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            v.frame?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            CapaDeteccion(v.estado, cfg, Modifier.fillMaxSize())
        }
        Text("Multas generadas: ${v.multas}", Modifier.padding(top = 8.dp))
        val vel = v.estado?.velocidades.orEmpty()
        if (vel.isNotEmpty()) {
            Text(vel.joinToString("  ·  ") { "#${it.first}: %.0f km/h".format(it.second) })
        }
    }
}
