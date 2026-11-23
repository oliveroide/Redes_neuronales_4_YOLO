package mx.radar.yolo.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.Composable
import mx.radar.yolo.radar.Config
import mx.radar.yolo.radar.EstadoRadar
import kotlin.math.min

private val AMARILLO = Color(0xFFFFC800)
private val VERDE = Color(0xFF00C800)
private val ROJO = Color(0xFFFF0000)
private val GRIS = Color(0xFFC8C8C8)

/**
 * Dibuja líneas A/B, cajas y etiquetas sobre una vista que muestra el cuadro con FIT_CENTER
 * (mismo mapeo que PreviewView.ScaleType.FIT_CENTER / ContentScale.Fit).
 */
@Composable
fun CapaDeteccion(estado: EstadoRadar?, cfg: Config, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val e = estado ?: return@Canvas
        val s = min(size.width / e.ancho, size.height / e.alto)
        val ox = (size.width - e.ancho * s) / 2f
        val oy = (size.height - e.alto * s) / 2f
        fun px(x: Float) = ox + x * s
        fun py(y: Float) = oy + y * s

        val pintura = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; isFakeBoldText = true }
        val l = if (cfg.vertical) e.ancho else e.alto
        for ((frac, nombre) in listOf(cfg.lineaA to "A", cfg.lineaB to "B")) {
            val pos = (frac * l).toInt().toFloat()
            if (cfg.vertical) {
                drawLine(AMARILLO, Offset(px(pos), py(0f)), Offset(px(pos), py(e.alto.toFloat())), 4f)
                pintura.color = android.graphics.Color.rgb(255, 200, 0)
                drawContext.canvas.nativeCanvas.drawText(nombre, px(pos) + 8f, py(0f) + 40f, pintura)
            } else {
                drawLine(AMARILLO, Offset(px(0f), py(pos)), Offset(px(e.ancho.toFloat()), py(pos)), 4f)
                pintura.color = android.graphics.Color.rgb(255, 200, 0)
                drawContext.canvas.nativeCanvas.drawText(nombre, px(0f) + 8f, py(pos) - 8f, pintura)
            }
        }

        for (c in e.cajas) {
            val (color, texto) = when {
                c.kmh == null -> GRIS to "#${c.id}"
                c.exceso -> ROJO to "#${c.id} ${c.kmh.toInt()} km/h EXCESO"
                else -> VERDE to "#${c.id} ${c.kmh.toInt()} km/h"
            }
            drawRect(color, Offset(px(c.x1), py(c.y1)), Size((c.x2 - c.x1) * s, (c.y2 - c.y1) * s), style = Stroke(4f))
            val ty = maxOf(py(c.y1) - 10f, 36f)
            pintura.style = Paint.Style.STROKE; pintura.strokeWidth = 7f; pintura.color = android.graphics.Color.BLACK
            drawContext.canvas.nativeCanvas.drawText(texto, px(c.x1), ty, pintura)
            pintura.style = Paint.Style.FILL; pintura.color = color.toArgbCompat()
            drawContext.canvas.nativeCanvas.drawText(texto, px(c.x1), ty, pintura)
        }
    }
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)
