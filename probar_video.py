# Procesa un video grabado con el radar: video anotado, multas y gráfica de velocidades
# Uso: python probar_video.py video.mp4 --limite 50 --distancia 20 --horizontal

import argparse

import cv2
import matplotlib.pyplot as plt
from ultralytics import YOLO

from radar import Config, LectorPlacas, Radar

ap = argparse.ArgumentParser()
ap.add_argument("video")
ap.add_argument("--limite", type=float, default=40)
ap.add_argument("--distancia", type=float, default=10)
ap.add_argument("--linea_a", type=float, default=0.30)
ap.add_argument("--linea_b", type=float, default=0.70)
ap.add_argument("--horizontal", action="store_true", help="autos suben/bajan en la imagen")
ap.add_argument("--ancho", type=int, default=1280)
args = ap.parse_args()

cfg = Config(limite_kmh=args.limite, distancia_m=args.distancia, linea_a=args.linea_a,
             linea_b=args.linea_b, vertical=not args.horizontal)
radar = Radar(YOLO("yolo11n.pt"), LectorPlacas(), cfg)

cap = cv2.VideoCapture(args.video)
fps = cap.get(cv2.CAP_PROP_FPS) or 30
salida = None
i = 0
while True:
    ok, frame = cap.read()
    if not ok:
        break
    escala = args.ancho / frame.shape[1]
    frame = cv2.resize(frame, None, fx=escala, fy=escala)
    # Tiempo del video (no el reloj) para que la velocidad no dependa de la rapidez de la PC
    anotado = radar.procesar(frame, t=i / fps)
    if salida is None:
        h, w = anotado.shape[:2]
        salida = cv2.VideoWriter("video_radar.mp4", cv2.VideoWriter_fourcc(*"mp4v"), fps, (w, h))
    salida.write(anotado)
    i += 1
salida.release()
radar._pool.shutdown(wait=True)

print(f"Cuadros procesados: {i}")
for m in radar.obtener_multas():
    print(f"MULTA {m.folio}  placa={m.placa}  {m.velocidad:.1f} km/h  -> {m.ruta_ticket}")

ids = sorted(radar.velocidades)
vel = [radar.velocidades[k] for k in ids]
colores = ["red" if v > cfg.limite_kmh else "green" for v in vel]
plt.bar([f"#{k}" for k in ids], vel, color=colores)
plt.axhline(cfg.limite_kmh, color="black", linestyle="--", label=f"Límite {cfg.limite_kmh:.0f} km/h")
plt.title("Velocidad medida por vehículo")
plt.xlabel("ID del vehículo")
plt.ylabel("km/h")
plt.legend()
plt.savefig("velocidades.png", dpi=120, bbox_inches="tight")
print("Guardado: video_radar.mp4 y velocidades.png")
