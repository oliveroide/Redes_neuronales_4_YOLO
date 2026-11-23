# Radar YOLO como app Android (Kotlin)

## Contexto
La versión Streamlit + ngrok no funcionó afuera: la cámara del celular va por WebRTC y
necesita red/TURN, y en la calle no había internet. Se quiere una app nativa Kotlin para
el Pixel donde **YOLO corra en el celular**, sin laptop ni internet. El proyecto Python se
conserva, renombrado y movido a una carpeta legacy, porque sirve para exportar los modelos
y como referencia de la lógica (`radar.py`).

**Decisiones:** inferencia en el celular · compilar con Android Studio · Python a carpeta legacy.

## 1. Mover el proyecto Python a `legacy_streamlit/`
- `git mv` de `app.py`, `radar.py`, `probar_video.py`, `requirements.txt` y
  `Explicacion_codigo_radar_YOLO.pdf` a `legacy_streamlit/`.
- `legacy_streamlit/README.md` = README actual (cómo correr la versión web).
- `radar.py` usa `Path(__file__).parent / "tickets"`, así que sigue funcionando movido.
- `.venv/` se queda en la raíz (solo cambia la ruta de los comandos).
- `.certs/` y la config TURN quedan solo en legacy; no se usan en Android.

## 2. Exportar modelos a TFLite — `tools/exportar_modelos.py`
Usa el `.venv` existente (ya tiene ultralytics y huggingface_hub):
- `YOLO("yolo11n.pt").export(format="tflite", imgsz=640, half=True)` → `yolo11n.tflite`
- Placas: `hf_hub_download("morsetechlab/yolov11-license-plate-detection",
  "license-plate-finetune-v1n.pt")` → misma exportación (imgsz 320) → `placas.tflite`
- Copia ambos a `android/app/src/main/assets/`.
- Salida YOLO11 TFLite: tensor `[1, 4+nc, N]` (cx, cy, w, h normalizados + scores).

## 3. App Android — `android/`
Kotlin, Jetpack Compose, minSdk 26, target 35. Gradle con version catalog.

**Dependencias:** CameraX (core, camera2, lifecycle, view) · LiteRT
(`com.google.ai.edge.litert:litert` + `litert-gpu`, con fallback a CPU) ·
ML Kit Text Recognition (latin, incluido en el APK, funciona offline) · Compose Material3.

**Paquete `mx.radar.yolo`:**

| Archivo | Qué hace |
|---|---|
| `ml/Detector.kt` | Carga el `.tflite`, letterbox a 640, corre el intérprete, decodifica `[1,84,N]`, filtra clases COCO 2/3/5/7 (Auto, Moto, Autobús, Camión) y `conf`, aplica NMS por IoU. Reutilizable para placas (1 clase). |
| `ml/Tracker.kt` | Tracker IoU simple (reemplaza ByteTrack): asociación greedy por IoU ≥ 0.3, IDs incrementales, olvida pistas no vistas en 10 s. |
| `radar/Radar.kt` | Port directo de `Radar.procesar` (`radar.py:177`): `Config` (límite, distancia, líneas A/B, vertical, conf), punto de referencia (centro-x si vertical, borde inferior si horizontal), cruce de línea, `v = d/|tB−tA|·3.6` con `dt > 0.05`, guarda el recorte más grande por vehículo, una sola multa por ID en un executor aparte. Tiempo = `imageInfo.timestamp` (en video, el tiempo del cuadro). |
| `radar/LectorPlacas.kt` | Port de `LectorPlacas.leer`: detector de placas TFLite con márgenes 6 %/10 %, OCR con ML Kit, misma limpieza (`[^A-Z0-9-]`, ≥ 4 caracteres), respaldo OCR en la mitad inferior, si no "NO LEGIBLE". |
| `radar/Ticket.kt` | Port de `dibujar_ticket` con `Canvas` → `Bitmap`. Guarda el PNG en `Pictures/RadarYOLO` (MediaStore) y agrega una fila a `multas.csv`. |
| `camara/RadarAnalyzer.kt` | `ImageAnalysis.Analyzer` (KEEP_ONLY_LATEST, RGBA_8888), rota el bitmap según `rotationDegrees`, llama a `Radar` y publica cajas y velocidades en un `StateFlow`. |
| `ui/RadarScreen.kt` | `PreviewView` + `Canvas` encima con líneas A/B, cajas y etiquetas (gris / verde km/h / rojo EXCESO), FPS y alerta con el ticket al haber multa. |
| `ui/AjustesSheet.kt` | Límite, distancia (m), sentido, RangeSlider para A y B, confianza. Persistido en DataStore. |
| `ui/MultasScreen.kt` | Historial con miniatura del ticket y botón Compartir. |
| `ui/VideoScreen.kt` | Elige un video de la galería y lo procesa cuadro por cuadro con `MediaMetadataRetriever` (tiempo del video, como `probar_video.py`); muestra progreso, velocidades y multas. |
| `MainActivity.kt` | Navegación (Radar · Video · Multas), permiso de cámara, pantalla encendida, orientación vertical. |

## 4. Repo
- `README.md` raíz nuevo: qué es, instalar Android Studio
  (`sudo snap install android-studio --classic`), abrir `android/`, conectar el Pixel con
  depuración USB y Run, cómo regenerar los modelos y enlace a `legacy_streamlit/`.
- `.gitignore`: `android/.gradle`, `android/build`, `android/app/build`, `local.properties`
  y `*.tflite` si se decide no versionarlos.

## Razones de las decisiones

- **YOLO en el celular:** lo que falló fue la red (WebRTC, ngrok, TURN) y afuera no había internet. Sin servidor no hay red que falle, y baja la latencia.
- **CameraX con `KEEP_ONLY_LATEST`:** descarta cuadros viejos si YOLO va lento, así el radar analiza siempre el presente.
- **Tiempo del sensor (`imageInfo.timestamp`):** la velocidad es `d/(tB−tA)`, por lo que el error de tiempo pasa directo a los km/h. El timestamp marca cuándo se capturó el cuadro, no cuándo se procesó.
- **YOLO11n a TFLite/LiteRT, float16, 640 px:** Android no ejecuta `.pt`. LiteRT es el motor oficial y usa GPU. La versión nano da tiempo real en un teléfono y float16 reduce el tamaño a la mitad sin pérdida notable.
- **Mismas clases COCO (2, 3, 5, 7):** el modelo preentrenado ya reconoce vehículos, no hay que reentrenar.
- **Tracker IoU en vez de ByteTrack:** ByteTrack vive dentro de ultralytics, que no existe en Android. Con cámara fija y pocos autos basta asociar por solapamiento.
- **Port directo de `Radar.procesar`:** esa lógica ya está probada con tu video; si el código es igual, las velocidades de Android y Python deben parecerse.
- **Detector de placas + ML Kit:** el OCR lee mucho mejor sobre la placa recortada. EasyOCR depende de PyTorch y no corre en Android; ML Kit viene en el APK y funciona sin internet.
- **OCR en otro hilo:** leer la placa tarda más que un cuadro; en el mismo hilo congelaría el video.
- **Ticket en galería:** se puede ver y compartir por WhatsApp sin cables.
- **Pantalla de video:** ya grabaste videos en la calle; sirve para analizarlos en el celular y comparar con Python.
- **Orientación vertical fija:** tus videos son verticales y evita que se descuadren las líneas al girar.

## Verificación
1. `tools/exportar_modelos.py` corre y deja los dos `.tflite` en assets; un script corto compara detecciones TFLite vs `.pt` en un cuadro de `PXL_20261120_212854972.mp4`.
2. Instalar Android Studio y compilar con `./gradlew assembleDebug` (o Run desde el IDE con el Pixel por USB).
3. Pantalla **Video** con `PXL_20261120_212854972.mp4` y distancia 10 m: ~2 vehículos con velocidades del orden de las de Python (≈ 28 y 20 km/h).
4. Modo cámara en el Pixel: cajas y líneas alineadas con la vista previa, FPS visibles; bajar el límite a 10 km/h para forzar una multa y revisar que el ticket se guarde en la galería y se pueda compartir.
5. `python legacy_streamlit/probar_video.py <video>` sigue funcionando.

## Riesgos
- La laptop tiene 8 GB de RAM: Android Studio va justo, por eso se usa el Pixel por USB y no el emulador.
- El tracker simple puede cambiar el ID si dos autos se cruzan o se tapan; con cámara de lado y tráfico normal casi no pasa.
