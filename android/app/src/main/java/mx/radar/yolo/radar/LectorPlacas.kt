package mx.radar.yolo.radar

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import mx.radar.yolo.ml.Detector

/** Detector de placas (YOLO11 TFLite) + OCR de ML Kit (funciona sin internet). */
class LectorPlacas(context: Context) {
    private val detector = Detector(context, "placas.tflite")
    private val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private fun leerTexto(img: Bitmap): String {
        if (img.width < 2 || img.height < 2) return ""
        // escalar recortes chicos: el OCR lee mejor con ~120 px de alto
        val src = if (img.height < 120) {
            val f = 120f / img.height
            Bitmap.createScaledBitmap(img, (img.width * f).toInt(), 120, true)
        } else img
        val res = Tasks.await(ocr.process(InputImage.fromBitmap(src, 0)))
        val texto = res.text.uppercase().replace(Regex("[^A-Z0-9-]"), "").trim('-')
        return if (texto.replace("-", "").length >= 4) texto else ""
    }

    /** Devuelve (texto, recorte de la placa o null). Llamar fuera del hilo principal. */
    @Synchronized
    fun leer(auto: Bitmap): Pair<String, Bitmap?> {
        val dets = detector.detectar(auto, conf = 0.2f)
        val mejor = dets.maxByOrNull { it.score }
        if (mejor != null) {
            val mx = ((mejor.x2 - mejor.x1) * 0.06f).toInt()
            val my = ((mejor.y2 - mejor.y1) * 0.10f).toInt()
            val x1 = (mejor.x1.toInt() - mx).coerceAtLeast(0)
            val y1 = (mejor.y1.toInt() - my).coerceAtLeast(0)
            val x2 = (mejor.x2.toInt() + mx).coerceAtMost(auto.width)
            val y2 = (mejor.y2.toInt() + my).coerceAtMost(auto.height)
            if (x2 - x1 > 1 && y2 - y1 > 1) {
                val placa = Bitmap.createBitmap(auto, x1, y1, x2 - x1, y2 - y1)
                val texto = leerTexto(placa)
                if (texto.isNotEmpty()) return texto to placa
            }
        }
        // Respaldo: OCR en la mitad inferior del vehículo
        val h = auto.height
        val mitad = Bitmap.createBitmap(auto, 0, h / 2, auto.width, h - h / 2)
        return leerTexto(mitad).ifEmpty { "NO LEGIBLE" } to null
    }
}
