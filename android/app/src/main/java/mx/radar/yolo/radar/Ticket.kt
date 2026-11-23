package mx.radar.yolo.radar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import java.io.File

class Multa(
    val folio: String, val fechaHora: String, val vehiculo: String, val placa: String,
    val velocidad: Float, val limite: Float, val exceso: Float,
) {
    lateinit var ticket: Bitmap
    var uri: Uri? = null
}

data class MultaGuardada(val uri: Uri, val nombre: String)

object Ticket {
    private val ROJO = Color.rgb(190, 20, 30)

    private fun escalar(b: Bitmap, ancho: Int) =
        Bitmap.createScaledBitmap(b, ancho, maxOf(1, b.height * ancho / b.width), true)

    /** Port de dibujar_ticket (radar.py). */
    fun dibujar(m: Multa, auto: Bitmap, placa: Bitmap?): Bitmap {
        val W = 620
        val foto = escalar(auto, W - 40)
        val fotoPlaca = placa?.let { escalar(it, 260) }
        val alto = 130 + 40 * 7 + foto.height + (fotoPlaca?.let { it.height + 20 } ?: 0) + 80
        val bmp = Bitmap.createBitmap(W, alto, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.color = ROJO
        c.drawRect(0f, 0f, W.toFloat(), 95f, p)
        p.color = Color.WHITE
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 34f
        c.drawText("MULTA SIMULADA", 20f, 44f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = 22f
        c.drawText("Exceso de velocidad - Radar YOLO", 20f, 80f, p)

        val datos = listOf(
            "Folio" to m.folio,
            "Fecha y hora" to m.fechaHora,
            "Vehículo" to m.vehiculo,
            "Placa" to m.placa,
            "Velocidad medida" to "%.1f km/h".format(m.velocidad),
            "Límite permitido" to "%.0f km/h".format(m.limite),
            "Exceso" to "+%.1f km/h".format(m.exceso),
        )
        var y = 115f
        for ((etiqueta, valor) in datos) {
            p.typeface = Typeface.DEFAULT
            p.textSize = 22f
            p.color = Color.rgb(80, 80, 80)
            c.drawText("$etiqueta:", 20f, y + 22f, p)
            p.textSize = 24f
            p.typeface = Typeface.DEFAULT_BOLD
            p.color = if (etiqueta in setOf("Placa", "Velocidad medida", "Exceso")) ROJO else Color.BLACK
            c.drawText(valor, 250f, y + 24f, p)
            y += 40f
        }
        y += 15f
        c.drawBitmap(foto, 20f, y, null)
        y += foto.height + 20f
        if (fotoPlaca != null) {
            c.drawBitmap(fotoPlaca, ((W - fotoPlaca.width) / 2).toFloat(), y, null)
        }
        p.typeface = Typeface.DEFAULT
        p.textSize = 18f
        p.color = Color.rgb(120, 120, 120)
        c.drawText("Documento de prueba sin validez oficial.", 20f, alto - 32f, p)
        return bmp
    }
}

/** Guarda tickets en Pictures/RadarYOLO (galería) y una fila en multas.csv. */
class TicketStore(private val context: Context) {
    private val carpeta = "Pictures/RadarYOLO"

    fun guardar(m: Multa) {
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "multa_${m.folio}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, carpeta)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return
        context.contentResolver.openOutputStream(uri)?.use { m.ticket.compress(Bitmap.CompressFormat.PNG, 100, it) }
        m.uri = uri

        val csv = File(context.getExternalFilesDir(null), "multas.csv")
        val nuevo = !csv.exists()
        csv.appendText(buildString {
            if (nuevo) appendLine("folio,fecha_hora,vehiculo,placa,velocidad_kmh,limite_kmh,exceso_kmh,ticket")
            appendLine("${m.folio},${m.fechaHora},${m.vehiculo},${m.placa},%.1f,%.0f,%.1f,multa_${m.folio}.png"
                .format(m.velocidad, m.limite, m.exceso))
        })
    }

    fun listar(): List<MultaGuardada> {
        val salida = ArrayList<MultaGuardada>()
        val cols = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols,
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf("$carpeta%"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                salida.add(MultaGuardada(uri, c.getString(1)))
            }
        }
        return salida
    }

    fun miniatura(uri: Uri): Bitmap? =
        try { context.contentResolver.loadThumbnail(uri, Size(240, 240), null) } catch (e: Exception) { null }
}
