package mx.radar.yolo.radar

import android.graphics.Bitmap
import mx.radar.yolo.ml.Detector
import mx.radar.yolo.ml.Tracker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

val VEHICULOS = mapOf(2 to "Auto", 3 to "Moto", 5 to "Autobus", 7 to "Camion")

data class Config(
    val limiteKmh: Float = 40f,
    val distanciaM: Float = 10f,
    val lineaA: Float = 0.30f, // fracción del ancho (vertical) o de la altura (horizontal)
    val lineaB: Float = 0.70f,
    val conf: Float = 0.4f,
    val vertical: Boolean = true, // true: los autos van de izquierda a derecha (cámara de lado)
)

data class Caja(
    val id: Int, val x1: Float, val y1: Float, val x2: Float, val y2: Float,
    val clase: String, val kmh: Float?, val exceso: Boolean,
)

data class EstadoRadar(
    val ancho: Int, val alto: Int,
    val cajas: List<Caja>,
    val velocidades: List<Pair<Int, Float>>,
)

private class Pista {
    var ref: Float? = null
    var a: Double? = null
    var b: Double? = null
    var visto = 0.0
    var area = 0f
    var recorte: Bitmap? = null
}

/** Port de Radar.procesar (radar.py): velocidad entre dos líneas con el tiempo del cuadro. */
class Radar(
    private val detector: Detector,
    private val lector: LectorPlacas?,
    private val cfgProvider: () -> Config,
    private val alMultar: (Multa) -> Unit,
) {
    private val tracker = Tracker()
    private val pistas = HashMap<Int, Pista>()
    private val velocidades = LinkedHashMap<Int, Float>()
    private val multados = HashSet<Int>()
    private val pool = Executors.newSingleThreadExecutor()

    private fun cruce(prev: Float?, y: Float, linea: Float) =
        prev != null && prev != y && (prev - linea) * (y - linea) <= 0f

    /** [frame] ya rotado en vertical/horizontal real; [t] en segundos (tiempo del cuadro). */
    fun procesar(frame: Bitmap, t: Double): EstadoRadar {
        val cfg = cfgProvider()
        val w = frame.width
        val h = frame.height
        val largo = if (cfg.vertical) w else h
        val ya = (cfg.lineaA * largo).toInt().toFloat()
        val yb = (cfg.lineaB * largo).toInt().toFloat()

        val dets = detector.detectar(frame, cfg.conf)
        val seguidos = tracker.actualizar(dets, t)
        val cajas = ArrayList<Caja>()

        for ((tid, d) in seguidos) {
            // vertical: centro horizontal de la caja; horizontal: punto inferior (piso)
            val y = if (cfg.vertical) ((d.x1 + d.x2) / 2f).toInt().toFloat() else d.y2.toInt().toFloat()
            val p = pistas.getOrPut(tid) { Pista() }
            p.visto = t
            if (p.a == null && cruce(p.ref, y, ya)) p.a = t
            if (p.b == null && cruce(p.ref, y, yb)) p.b = t
            p.ref = y

            // Guardar el recorte más grande del vehículo (mejor oportunidad de leer la placa)
            if (tid !in multados && d.area > p.area) {
                val m = 10
                val x1 = (d.x1.toInt() - m).coerceAtLeast(0)
                val y1 = (d.y1.toInt() - m).coerceAtLeast(0)
                val x2 = (d.x2.toInt() + m).coerceAtMost(w)
                val y2 = (d.y2.toInt() + m).coerceAtMost(h)
                if (x2 - x1 > 1 && y2 - y1 > 1) {
                    p.area = d.area
                    p.recorte = Bitmap.createBitmap(frame, x1, y1, x2 - x1, y2 - y1)
                }
            }

            val pa = p.a
            val pb = p.b
            if (tid !in velocidades && pa != null && pb != null) {
                val dt = abs(pb - pa)
                if (dt > 0.05) {
                    val kmh = (cfg.distanciaM / dt * 3.6).toFloat()
                    velocidades[tid] = kmh
                    val recorte = p.recorte
                    if (kmh > cfg.limiteKmh && tid !in multados && recorte != null) {
                        multados.add(tid)
                        p.recorte = null
                        val veh = VEHICULOS[d.cls] ?: "Vehiculo"
                        pool.submit { multar(tid, veh, recorte, kmh, cfg) }
                    }
                }
            }
            val kmh = velocidades[tid]
            cajas.add(Caja(tid, d.x1, d.y1, d.x2, d.y2, VEHICULOS[d.cls] ?: "?", kmh, kmh != null && kmh > cfg.limiteKmh))
        }

        // Olvidar vehículos que ya no se ven
        pistas.entries.removeAll { t - it.value.visto > 10 }
        return EstadoRadar(w, h, cajas, velocidades.map { it.key to it.value })
    }

    private fun multar(tid: Int, vehiculo: String, recorte: Bitmap, kmh: Float, cfg: Config) {
        try {
            val (placa, imgPlaca) = lector?.leer(recorte) ?: ("NO LEGIBLE" to null)
            val ahora = Date()
            val folio = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(ahora) + "-$tid"
            val m = Multa(
                folio = folio,
                fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(ahora),
                vehiculo = vehiculo, placa = placa, velocidad = kmh,
                limite = cfg.limiteKmh, exceso = kmh - cfg.limiteKmh,
            )
            m.ticket = Ticket.dibujar(m, recorte, imgPlaca)
            alMultar(m)
        } catch (e: Exception) { // no tumbar el hilo del análisis
            android.util.Log.e("Radar", "error generando multa de #$tid", e)
        }
    }

    fun cerrar() = pool.shutdown()
}
