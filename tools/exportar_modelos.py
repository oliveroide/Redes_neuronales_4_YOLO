# Exporta los modelos YOLO a TFLite y los copia a los assets de la app Android.
#
# Uso (desde la raíz del repo, en LINUX x86 o macOS, p. ej. WSL Ubuntu):
#   pip install ultralytics huggingface_hub
#   python tools/exportar_modelos.py
#
# Ultralytics 8.4 reemplazó format="tflite" por format="litert" (FP32 o INT8, sin FP16) y
# la exportación LiteRT NO funciona en Windows. Si tu Python es muy nuevo, usa un venv 3.10-3.12.

import shutil
from pathlib import Path

from huggingface_hub import hf_hub_download
from ultralytics import YOLO

RAIZ = Path(__file__).resolve().parent.parent
ASSETS = RAIZ / "android" / "app" / "src" / "main" / "assets"
REPO_PLACAS = "morsetechlab/yolov11-license-plate-detection"
PESOS_PLACAS = "license-plate-finetune-v1n.pt"


def exportar(pesos: str, imgsz: int, destino: str):
    ruta = YOLO(pesos).export(format="litert", imgsz=imgsz)
    ruta = Path(ruta)
    if ruta.is_dir():  # versiones nuevas devuelven la carpeta *_saved_model
        candidatos = sorted(ruta.glob("*float16.tflite")) or sorted(ruta.glob("*.tflite"))
        ruta = candidatos[0]
    ASSETS.mkdir(parents=True, exist_ok=True)
    shutil.copy(ruta, ASSETS / destino)
    print(f"OK {destino}  <-  {ruta}")


if __name__ == "__main__":
    exportar("yolo11n.pt", 640, "yolo11n.tflite")
    exportar(hf_hub_download(REPO_PLACAS, PESOS_PLACAS), 320, "placas.tflite")
