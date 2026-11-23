package mx.radar.yolo.camara

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import mx.radar.yolo.radar.EstadoRadar
import mx.radar.yolo.radar.Radar

/** Recibe cuadros RGBA de CameraX, los endereza y los pasa al radar con el tiempo del sensor. */
class RadarAnalyzer(
    private val radar: Radar,
    private val publicar: (EstadoRadar, Float) -> Unit,
) : ImageAnalysis.Analyzer {
    private var ultimo = 0L
    private var fps = 0f

    override fun analyze(image: ImageProxy) {
        try {
            var bmp = image.toBitmap()
            val rot = image.imageInfo.rotationDegrees
            if (rot != 0) {
                val m = Matrix().apply { postRotate(rot.toFloat()) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, false)
            }
            // el timestamp marca cuándo se capturó el cuadro, no cuándo se procesó
            val t = image.imageInfo.timestamp / 1e9
            val estado = radar.procesar(bmp, t)

            val ahora = System.nanoTime()
            if (ultimo != 0L) {
                val inst = 1e9f / (ahora - ultimo)
                fps = if (fps == 0f) inst else fps * 0.8f + inst * 0.2f
            }
            ultimo = ahora
            publicar(estado, fps)
        } catch (e: Exception) {
            android.util.Log.e("RadarAnalyzer", "error en el cuadro", e)
        } finally {
            image.close()
        }
    }
}
