package mx.radar.yolo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.radar.yolo.radar.Config
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesSheet(cfg: Config, alCambiar: (Config) -> Unit, alCerrar: () -> Unit) {
    ModalBottomSheet(onDismissRequest = alCerrar) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 24.dp)) {
            Text("Límite: ${cfg.limiteKmh.roundToInt()} km/h")
            Slider(cfg.limiteKmh, { alCambiar(cfg.copy(limiteKmh = it.roundToInt().toFloat())) }, valueRange = 5f..120f)

            Text("Distancia entre líneas A y B: %.1f m".format(cfg.distanciaM))
            Slider(cfg.distanciaM, { alCambiar(cfg.copy(distanciaM = (it * 2).roundToInt() / 2f)) }, valueRange = 1f..50f)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (cfg.vertical) "Autos cruzan de lado (líneas verticales)" else "Autos de frente (líneas horizontales)",
                    Modifier.weight(1f))
                Switch(cfg.vertical, { alCambiar(cfg.copy(vertical = it)) })
            }

            Text("Líneas: A %.0f %%  ·  B %.0f %%".format(cfg.lineaA * 100, cfg.lineaB * 100))
            RangeSlider(
                value = cfg.lineaA..cfg.lineaB,
                onValueChange = { alCambiar(cfg.copy(lineaA = it.start, lineaB = it.endInclusive)) },
                valueRange = 0.05f..0.95f,
            )

            Text("Confianza mínima: %.2f".format(cfg.conf))
            Slider(cfg.conf, { alCambiar(cfg.copy(conf = (it * 20).roundToInt() / 20f)) }, valueRange = 0.1f..0.9f)
        }
    }
}
