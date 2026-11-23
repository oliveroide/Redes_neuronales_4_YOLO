package mx.radar.yolo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.radar.yolo.ui.MultasScreen
import mx.radar.yolo.ui.RadarScreen
import mx.radar.yolo.ui.VideoScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // pantalla encendida mientras mide
        setContent {
            MaterialTheme {
                val vm: RadarViewModel = viewModel()
                var tab by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(bottomBar = {
                    NavigationBar {
                        listOf(
                            Triple("Radar", Icons.Default.CameraAlt, 0),
                            Triple("Video", Icons.Default.VideoFile, 1),
                            Triple("Multas", Icons.Default.Receipt, 2),
                        ).forEach { (nombre, icono, i) ->
                            NavigationBarItem(tab == i, { tab = i }, { Icon(icono, nombre) }, label = { Text(nombre) })
                        }
                    }
                }) { pad ->
                    Box(Modifier.fillMaxSize().padding(pad)) {
                        when (tab) {
                            0 -> RadarScreen(vm)
                            1 -> VideoScreen(vm)
                            else -> MultasScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
