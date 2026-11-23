package mx.radar.yolo

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.radar.yolo.ml.Detector
import mx.radar.yolo.radar.Config
import mx.radar.yolo.radar.EstadoRadar
import mx.radar.yolo.radar.LectorPlacas
import mx.radar.yolo.radar.Multa
import mx.radar.yolo.radar.MultaGuardada
import mx.radar.yolo.radar.Radar
import mx.radar.yolo.radar.TicketStore
import mx.radar.yolo.radar.VEHICULOS

private val android.content.Context.ajustes by preferencesDataStore("ajustes")

data class EstadoVideo(
    val frame: Bitmap? = null,
    val estado: EstadoRadar? = null,
    val progreso: Float = 0f,
    val procesando: Boolean = false,
    val multas: Int = 0,
)

class RadarViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val kLimite = floatPreferencesKey("limite")
    private val kDist = floatPreferencesKey("distancia")
    private val kA = floatPreferencesKey("linea_a")
    private val kB = floatPreferencesKey("linea_b")
    private val kConf = floatPreferencesKey("conf")
    private val kVert = booleanPreferencesKey("vertical")

    val config: StateFlow<Config> = ctx.ajustes.data.map { p ->
        val d = Config()
        Config(
            limiteKmh = p[kLimite] ?: d.limiteKmh, distanciaM = p[kDist] ?: d.distanciaM,
            lineaA = p[kA] ?: d.lineaA, lineaB = p[kB] ?: d.lineaB,
            conf = p[kConf] ?: d.conf, vertical = p[kVert] ?: d.vertical,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Config())

    fun guardarConfig(c: Config) {
        viewModelScope.launch {
            ctx.ajustes.edit {
                it[kLimite] = c.limiteKmh; it[kDist] = c.distanciaM; it[kA] = c.lineaA
                it[kB] = c.lineaB; it[kConf] = c.conf; it[kVert] = c.vertical
            }
        }
    }

    private val detector by lazy { Detector(ctx, "yolo11n.tflite", VEHICULOS.keys) }
    private val lector by lazy { LectorPlacas(ctx) }
    private val store = TicketStore(ctx)

    /** Última multa generada (la UI muestra la alerta y la limpia con [descartarMulta]). */
    val nuevaMulta = MutableStateFlow<Multa?>(null)
    val historial = MutableStateFlow<List<MultaGuardada>>(emptyList())

    fun descartarMulta() { nuevaMulta.value = null }
    fun cargarHistorial() {
        viewModelScope.launch(Dispatchers.IO) { historial.value = store.listar() }
    }
    fun miniatura(uri: Uri) = store.miniatura(uri)

    private val videoMultas = MutableStateFlow(0)

    fun crearRadar(): Radar = Radar(detector, lector, { config.value }) { m ->
        store.guardar(m)
        nuevaMulta.value = m
        videoMultas.value += 1
    }

    // ---- cámara
    val estadoCamara = MutableStateFlow<EstadoRadar?>(null)
    val fps = MutableStateFlow(0f)

    // ---- video
    val video = MutableStateFlow(EstadoVideo())
    private var jobVideo: Job? = null

    fun procesarVideo(uri: Uri) {
        jobVideo?.cancel()
        videoMultas.value = 0
        jobVideo = viewModelScope.launch(Dispatchers.Default) {
            val radar = crearRadar()
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(ctx, uri)
                val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                val vw = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 960
                val vh = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 540
                val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                val fpsVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f
                // dimensiones del cuadro ya rotado, con máximo 960 px de lado mayor
                val (rw, rh) = if (rot == 90 || rot == 270) vh to vw else vw to vh
                val k = minOf(1f, 960f / maxOf(rw, rh))
                val paso = (1_000_000f / fpsVideo.coerceIn(10f, 60f)).toLong()
                val durUs = durMs * 1000
                video.value = EstadoVideo(procesando = true)
                var us = 0L
                while (us <= durUs && isActive) {
                    val frame = mmr.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST,
                        (rw * k).toInt(), (rh * k).toInt()) ?: break
                    val est = radar.procesar(frame, us / 1e6)
                    video.value = EstadoVideo(frame, est, us.toFloat() / durUs.coerceAtLeast(1), true, videoMultas.value)
                    us += paso
                }
                video.value = video.value.copy(procesando = false, progreso = 1f, multas = videoMultas.value)
            } catch (e: Exception) {
                android.util.Log.e("Video", "error procesando video", e)
                video.value = video.value.copy(procesando = false)
            } finally {
                mmr.release()
                withContext(kotlinx.coroutines.NonCancellable) { radar.cerrar() }
            }
        }
    }

    fun detenerVideo() { jobVideo?.cancel() }
}
