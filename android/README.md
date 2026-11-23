# Radar de velocidad con YOLO (app Android)

App nativa en Kotlin que mide la velocidad de los vehículos con la cámara del celular, **sin internet y sin laptop**: YOLO11 corre en el teléfono (LiteRT/TFLite). Si un vehículo rebasa el límite, lee la placa (detector de placas + ML Kit OCR) y genera una **multa simulada** que se guarda en la galería (`Pictures/RadarYOLO`) y se puede compartir.

La velocidad sale de dos líneas A y B separadas por una distancia conocida *d*:

v [km/h] = d / (t_B − t_A) × 3.6, con el tiempo de captura de cada cuadro.

## Estructura

```
├── android/                    # App Kotlin + Jetpack Compose (abrir esta carpeta en Android Studio)
├── tools/exportar_modelos.py   # .pt -> .tflite en android/app/src/main/assets/
└── PLAN_ANDROID.md
```

## Compilar e instalar

1. Instala Android Studio (Linux: `sudo snap install android-studio --classic`).
2. Genera los modelos (ver abajo) para que existan `yolo11n.tflite` y `placas.tflite` en `android/app/src/main/assets/`.
3. Abre la carpeta `android/` y espera el Gradle Sync.
4. Activa *Depuración USB* en el Pixel, conéctalo y presiona **Run**.

## Regenerar los modelos

```bash
pip install ultralytics huggingface_hub
python tools/exportar_modelos.py   # desde la raíz del repositorio
```

Ultralytics 8.4 exporta con `format="litert"`, que **solo funciona en Linux x86 o macOS** (en Windows usa WSL Ubuntu). Si tu Python es muy nuevo, usa un venv 3.10–3.12.

## Pantallas

- **Radar:** cámara en vivo con líneas A/B, cajas, velocidades y FPS. Ajustes (límite, distancia, sentido, líneas, confianza) con el engrane.
- **Video:** procesa un video de la galería cuadro por cuadro usando el tiempo del video.
- **Multas:** historial de tickets con botón para compartir.

La versión principal (Python/Streamlit) está en la raíz del repositorio: ver el [README](../README.md).
