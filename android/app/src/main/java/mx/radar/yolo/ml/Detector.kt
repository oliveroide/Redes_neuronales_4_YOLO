package mx.radar.yolo.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

data class Deteccion(
    val x1: Float, val y1: Float, val x2: Float, val y2: Float,
    val score: Float, val cls: Int,
) {
    val area get() = (x2 - x1) * (y2 - y1)
}

fun iou(a: Deteccion, b: Deteccion): Float {
    val iw = min(a.x2, b.x2) - max(a.x1, b.x1)
    val ih = min(a.y2, b.y2) - max(a.y1, b.y1)
    if (iw <= 0f || ih <= 0f) return 0f
    val inter = iw * ih
    return inter / (a.area + b.area - inter)
}

/**
 * YOLO11 en TFLite. Entrada [1,H,W,3] float 0..1; salida [1, 4+nc, N] (cx, cy, w, h, scores).
 * [clases] filtra las clases que interesan (null = todas).
 */
class Detector(
    private val context: Context,
    private val asset: String,
    private val clases: Set<Int>? = null,
) {
    private val modelo: ByteBuffer = context.assets.openFd(asset).use { fd ->
        FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }
    private var interp: Interpreter = crear(gpu = true)

    private val entrada = interp.getInputTensor(0).shape() // [1,3,H,W] (litert-torch) o [1,H,W,3]
    private val nchw = entrada[1] == 3
    private val alto = if (nchw) entrada[2] else entrada[1]
    private val ancho = if (nchw) entrada[3] else entrada[2]
    private val salidaShape = interp.getOutputTensor(0).shape()
    private val transpuesta = salidaShape[1] > salidaShape[2] // [1,N,C] en vez de [1,C,N]
    private val nC = if (transpuesta) salidaShape[2] else salidaShape[1]
    private val nN = if (transpuesta) salidaShape[1] else salidaShape[2]
    private val nClases = nC - 4

    private val bufEntrada = ByteBuffer.allocateDirect(4 * alto * ancho * 3).order(ByteOrder.nativeOrder())
    private val bufSalida = ByteBuffer.allocateDirect(4 * nC * nN).order(ByteOrder.nativeOrder())
    private val pixeles = IntArray(alto * ancho)
    private val lienzo = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(lienzo)
    private val pincel = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun crear(gpu: Boolean): Interpreter {
        if (gpu) {
            try {
                return Interpreter(modelo, Interpreter.Options().addDelegate(GpuDelegate()))
            } catch (e: Throwable) { /* sin GPU compatible: caer a CPU */ }
        }
        return Interpreter(modelo, Interpreter.Options().setNumThreads(4))
    }

    @Synchronized
    fun detectar(bmp: Bitmap, conf: Float, iouNms: Float = 0.45f): List<Deteccion> {
        val w = bmp.width
        val h = bmp.height
        // letterbox: escala uniforme y relleno gris centrado
        val escala = min(ancho.toFloat() / w, alto.toFloat() / h)
        val padX = (ancho - w * escala) / 2f
        val padY = (alto - h * escala) / 2f
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.save()
        canvas.translate(padX, padY)
        canvas.scale(escala, escala)
        canvas.drawBitmap(bmp, 0f, 0f, pincel)
        canvas.restore()

        lienzo.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
        bufEntrada.rewind()
        if (nchw) { // planos R, G, B
            val n = pixeles.size
            for (i in 0 until n) bufEntrada.putFloat(i * 4, ((pixeles[i] shr 16) and 0xFF) / 255f)
            for (i in 0 until n) bufEntrada.putFloat((n + i) * 4, ((pixeles[i] shr 8) and 0xFF) / 255f)
            for (i in 0 until n) bufEntrada.putFloat((2 * n + i) * 4, (pixeles[i] and 0xFF) / 255f)
        } else {
            for (p in pixeles) {
                bufEntrada.putFloat(((p shr 16) and 0xFF) / 255f)
                bufEntrada.putFloat(((p shr 8) and 0xFF) / 255f)
                bufEntrada.putFloat((p and 0xFF) / 255f)
            }
        }
        bufEntrada.rewind()
        bufSalida.rewind()
        try {
            interp.run(bufEntrada, bufSalida)
        } catch (e: Throwable) {
            // el delegado GPU puede fallar en la primera inferencia: reintentar en CPU
            interp.close()
            interp = crear(gpu = false)
            bufEntrada.rewind()
            bufSalida.rewind()
            interp.run(bufEntrada, bufSalida)
        }
        val s = bufSalida.asFloatBuffer()
        fun v(c: Int, i: Int) = if (transpuesta) s.get(i * nC + c) else s.get(c * nN + i)

        val ids = clases?.toIntArray() ?: IntArray(nClases) { it }
        val cand = ArrayList<Deteccion>()
        for (i in 0 until nN) {
            var mejor = -1f
            var cls = -1
            for (c in ids) {
                if (c >= nClases) continue
                val sc = v(4 + c, i)
                if (sc > mejor) { mejor = sc; cls = c }
            }
            if (mejor < conf) continue
            var cx = v(0, i); var cy = v(1, i); var bw = v(2, i); var bh = v(3, i)
            if (cx <= 1.5f && cy <= 1.5f && bw <= 1.5f && bh <= 1.5f) { // normalizado -> píxeles
                cx *= ancho; cy *= alto; bw *= ancho; bh *= alto
            }
            val x1 = ((cx - bw / 2 - padX) / escala).coerceIn(0f, w.toFloat())
            val y1 = ((cy - bh / 2 - padY) / escala).coerceIn(0f, h.toFloat())
            val x2 = ((cx + bw / 2 - padX) / escala).coerceIn(0f, w.toFloat())
            val y2 = ((cy + bh / 2 - padY) / escala).coerceIn(0f, h.toFloat())
            if (x2 > x1 && y2 > y1) cand.add(Deteccion(x1, y1, x2, y2, mejor, cls))
        }
        return nms(cand, iouNms)
    }

    private fun nms(cand: List<Deteccion>, umbral: Float): List<Deteccion> {
        val ord = cand.sortedByDescending { it.score }
        val keep = ArrayList<Deteccion>()
        for (d in ord) {
            if (keep.none { it.cls == d.cls && iou(it, d) > umbral }) keep.add(d)
        }
        return keep
    }
}
