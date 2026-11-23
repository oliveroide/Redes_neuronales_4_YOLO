package mx.radar.yolo.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import mx.radar.yolo.RadarViewModel
import mx.radar.yolo.camara.RadarAnalyzer
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(vm: RadarViewModel) {
    val ctx = LocalContext.current
    var permiso by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permiso = it }

    if (!permiso) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("El radar necesita la cámara")
            Button(onClick = { pedir.launch(Manifest.permission.CAMERA) }, Modifier.padding(16.dp)) { Text("Dar permiso") }
        }
        return
    }

    val cfg = vm.config.collectAsState().value
    val estado = vm.estadoCamara.collectAsState().value
    val fps = vm.fps.collectAsState().value
    val multa = vm.nuevaMulta.collectAsState().value
    var ajustes by remember { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current
    val vista = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }

    DisposableEffect(lifecycle) {
        val radar = vm.crearRadar()
        val hilo = Executors.newSingleThreadExecutor()
        val futuro = ProcessCameraProvider.getInstance(ctx)
        futuro.addListener({
            val proveedor = futuro.get()
            // misma relación 4:3 en vista previa y análisis para que las cajas coincidan con la imagen
            val sel = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO))
                .build()
            val preview = Preview.Builder().setResolutionSelector(sel).build()
                .also { it.surfaceProvider = vista.surfaceProvider }
            val analisis = ImageAnalysis.Builder()
                .setResolutionSelector(sel)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analisis.setAnalyzer(hilo, RadarAnalyzer(radar) { e, f -> vm.estadoCamara.value = e; vm.fps.value = f })
            proveedor.unbindAll()
            proveedor.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analisis)
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            futuro.addListener({ futuro.get().unbindAll() }, ContextCompat.getMainExecutor(ctx))
            hilo.shutdown()
            radar.cerrar()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ vista }, Modifier.fillMaxSize())
        CapaDeteccion(estado, cfg, Modifier.fillMaxSize())
        Row(
            Modifier.fillMaxWidth().background(Color(0x99000000)).padding(8.dp).align(Alignment.TopCenter),
            Arrangement.SpaceBetween, Alignment.CenterVertically,
        ) {
            Text("%.0f FPS · límite %.0f km/h · %.1f m".format(fps, cfg.limiteKmh, cfg.distanciaM), color = Color.White)
            IconButton(onClick = { ajustes = true }) { Icon(Icons.Default.Settings, "Ajustes", tint = Color.White) }
        }
    }

    if (ajustes) AjustesSheet(cfg, { vm.guardarConfig(it) }) { ajustes = false }

    multa?.let { m ->
        AlertDialog(
            onDismissRequest = vm::descartarMulta,
            confirmButton = { TextButton(onClick = vm::descartarMulta) { Text("Cerrar") } },
            title = { Text("Multa: ${m.placa} a %.0f km/h".format(m.velocidad)) },
            text = { Image(m.ticket.asImageBitmap(), "Ticket", Modifier.fillMaxWidth()) },
        )
    }
}
