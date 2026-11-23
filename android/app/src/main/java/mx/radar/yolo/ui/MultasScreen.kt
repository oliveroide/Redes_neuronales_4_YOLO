package mx.radar.yolo.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.radar.yolo.RadarViewModel
import mx.radar.yolo.radar.MultaGuardada

@Composable
fun MultasScreen(vm: RadarViewModel) {
    val lista = vm.historial.collectAsState().value
    LaunchedEffect(Unit) { vm.cargarHistorial() }

    if (lista.isEmpty()) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { Text("Aún no hay multas") }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(lista, key = { it.uri.toString() }) { Fila(vm, it) }
    }
}

@Composable
private fun Fila(vm: RadarViewModel, m: MultaGuardada) {
    val ctx = LocalContext.current
    val mini by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, m.uri) {
        value = withContext(Dispatchers.IO) { vm.miniatura(m.uri)?.asImageBitmap() }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        mini?.let { Image(it, null, Modifier.size(90.dp)) }
        Text(m.nombre.removePrefix("multa_").removeSuffix(".png"), Modifier.weight(1f).padding(horizontal = 10.dp))
        Button(onClick = {
            val envio = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, m.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(envio, "Compartir multa"))
        }) { Text("Compartir") }
    }
}
