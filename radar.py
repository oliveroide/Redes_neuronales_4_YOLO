# Radar de velocidad con YOLO: tracking, velocidad entre dos líneas, placa y multa simulada

import csv
import re
import threading
import time
from collections import deque
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, replace
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

VEHICULOS = {2: "Auto", 3: "Moto", 5: "Autobus", 7: "Camion"}
REPO_PLACAS = "morsetechlab/yolov11-license-plate-detection"
PESOS_PLACAS = "license-plate-finetune-v1n.pt"
CARPETA_TICKETS = Path(__file__).parent / "tickets"


@dataclass
class Config:
    limite_kmh: float = 40.0
    distancia_m: float = 10.0
    linea_a: float = 0.30  # fracción del ancho (vertical) o de la altura (horizontal)
    linea_b: float = 0.70
    conf: float = 0.4
    vertical: bool = True  # True: autos van de izquierda a derecha (cámara de lado)


@dataclass
class Multa:
    folio: str
    fecha_hora: str
    vehiculo: str
    placa: str
    velocidad: float
    limite: float
    exceso: float
    ruta_ticket: str
    img_ticket: np.ndarray  # RGB


# ---------------------------------------------------------------- placas
class LectorPlacas:
    # YOLO de placas (transfer learning sobre YOLO11) + OCR

    def __init__(self):
        import easyocr
        from huggingface_hub import hf_hub_download
        from ultralytics import YOLO

        self.detector = YOLO(hf_hub_download(REPO_PLACAS, PESOS_PLACAS))
        self.ocr = easyocr.Reader(["en"], gpu=False, verbose=False)
        self.lock = threading.Lock()

    def _ocr(self, img):
        if img.size == 0:
            return ""
        gris = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        escala = max(1.0, 120 / gris.shape[0])
        gris = cv2.resize(gris, None, fx=escala, fy=escala, interpolation=cv2.INTER_CUBIC)
        res = self.ocr.readtext(gris, allowlist="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-")
        res = sorted((r for r in res if r[2] > 0.2), key=lambda r: r[0][0][0])
        texto = re.sub(r"[^A-Z0-9-]", "", "".join(r[1] for r in res).upper()).strip("-")
        return texto if len(texto.replace("-", "")) >= 4 else ""

    def leer(self, img_auto):
        with self.lock:
            # No reescalar recortes chicos hasta 640 px: deforma las placas pequeñas
            tam = int(min(640, max(160, max(img_auto.shape[:2]))) // 32 * 32)
            det = self.detector.predict(img_auto, conf=0.2, imgsz=tam, verbose=False)[0]
            if len(det.boxes):
                i = int(det.boxes.conf.argmax())
                x1, y1, x2, y2 = det.boxes.xyxy[i].int().tolist()
                mx, my = int((x2 - x1) * 0.06), int((y2 - y1) * 0.1)  # margen para no cortar caracteres
                H, W = img_auto.shape[:2]
                placa = img_auto[max(0, y1 - my):min(H, y2 + my), max(0, x1 - mx):min(W, x2 + mx)]
                texto = self._ocr(placa)
                if texto:
                    return texto, placa
            # Respaldo: OCR en la mitad inferior del vehículo
            h = img_auto.shape[0]
            texto = self._ocr(img_auto[h // 2 :])
            return (texto or "NO LEGIBLE"), None


# ---------------------------------------------------------------- ticket
def _fuente(tam):
    for nombre in ("arial.ttf", "DejaVuSans.ttf", "LiberationSans-Regular.ttf"):
        try:
            return ImageFont.truetype(nombre, tam)
        except OSError:
            pass
    return ImageFont.load_default(size=tam)


def _a_pil(img_bgr, ancho):
    rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
    pil = Image.fromarray(rgb)
    alto = max(1, int(pil.height * ancho / pil.width))
    return pil.resize((ancho, alto))


def dibujar_ticket(m: Multa, img_auto, img_placa):
    W = 620
    foto = _a_pil(img_auto, W - 40)
    foto_placa = _a_pil(img_placa, 260) if img_placa is not None and img_placa.size else None
    alto = 130 + 40 * 7 + foto.height + (foto_placa.height + 20 if foto_placa else 0) + 80
    t = Image.new("RGB", (W, alto), "white")
    d = ImageDraw.Draw(t)
    d.rectangle([0, 0, W, 95], fill=(190, 20, 30))
    d.text((20, 14), "MULTA SIMULADA", font=_fuente(34), fill="white")
    d.text((20, 58), "Exceso de velocidad - Radar YOLO", font=_fuente(22), fill="white")

    datos = [
        ("Folio", m.folio),
        ("Fecha y hora", m.fecha_hora),
        ("Vehículo", m.vehiculo),
        ("Placa", m.placa),
        ("Velocidad medida", f"{m.velocidad:.1f} km/h"),
        ("Límite permitido", f"{m.limite:.0f} km/h"),
        ("Exceso", f"+{m.exceso:.1f} km/h"),
    ]
    y = 115
    for etiqueta, valor in datos:
        d.text((20, y), etiqueta + ":", font=_fuente(22), fill=(80, 80, 80))
        color = (190, 20, 30) if etiqueta in ("Placa", "Velocidad medida", "Exceso") else "black"
        d.text((250, y), valor, font=_fuente(24), fill=color)
        y += 40
    y += 15
    t.paste(foto, (20, y))
    y += foto.height + 20
    if foto_placa:
        t.paste(foto_placa, ((W - foto_placa.width) // 2, y))
        y += foto_placa.height + 20
    d.text((20, alto - 50), "Documento de prueba sin validez oficial.", font=_fuente(18), fill=(120, 120, 120))
    return np.array(t)


def guardar_multa(m: Multa):
    CARPETA_TICKETS.mkdir(exist_ok=True)
    Image.fromarray(m.img_ticket).save(m.ruta_ticket)
    csv_path = CARPETA_TICKETS / "multas.csv"
    nuevo = not csv_path.exists()
    with open(csv_path, "a", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        if nuevo:
            w.writerow(["folio", "fecha_hora", "vehiculo", "placa", "velocidad_kmh", "limite_kmh", "exceso_kmh", "ticket"])
        w.writerow([m.folio, m.fecha_hora, m.vehiculo, m.placa, f"{m.velocidad:.1f}", f"{m.limite:.0f}", f"{m.exceso:.1f}", Path(m.ruta_ticket).name])


# ---------------------------------------------------------------- radar
class Radar:
    def __init__(self, modelo_vehiculos, lector_placas=None, cfg=None):
        self.model = modelo_vehiculos
        self.placas = lector_placas
        self.cfg = cfg or Config()
        self.pistas = {}  # id -> {"y": y_prev, "a": t, "b": t, "visto": t}
        self.velocidades = {}  # id -> km/h
        self.multados = set()
        self._nuevas = deque()
        self._lock = threading.Lock()
        self._pool = ThreadPoolExecutor(max_workers=1)

    def obtener_multas(self):
        with self._lock:
            salida = list(self._nuevas)
            self._nuevas.clear()
        return salida

    def _cruce(self, y_prev, y, linea):
        return y_prev is not None and y_prev != y and (y_prev - linea) * (y - linea) <= 0

    def procesar(self, frame, t=None):
        # t en segundos; si no se da, se usa el reloj real
        t = time.monotonic() if t is None else t
        cfg = self.cfg
        h, w = frame.shape[:2]
        L = w if cfg.vertical else h
        ya, yb = int(cfg.linea_a * L), int(cfg.linea_b * L)

        res = self.model.track(frame, persist=True, tracker="bytetrack.yaml",
                               classes=list(VEHICULOS), conf=cfg.conf, verbose=False)[0]
        salida = frame.copy()
        for pos, nombre in ((ya, "A"), (yb, "B")):
            if cfg.vertical:
                cv2.line(salida, (pos, 0), (pos, h), (255, 200, 0), 2)
                cv2.putText(salida, nombre, (pos + 6, 28), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 200, 0), 2)
            else:
                cv2.line(salida, (0, pos), (w, pos), (255, 200, 0), 2)
                cv2.putText(salida, nombre, (8, pos - 8), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 200, 0), 2)

        if res.boxes.id is not None:
            for caja, tid, cls in zip(res.boxes.xyxy.int().tolist(), res.boxes.id.int().tolist(),
                                      res.boxes.cls.int().tolist()):
                x1, y1, x2, y2 = caja
                # vertical: centro horizontal de la caja; horizontal: punto inferior (piso)
                y = (x1 + x2) // 2 if cfg.vertical else y2
                p = self.pistas.setdefault(tid, {"y": None, "a": None, "b": None})
                p["visto"] = t
                if p["a"] is None and self._cruce(p["y"], y, ya):
                    p["a"] = t
                if p["b"] is None and self._cruce(p["y"], y, yb):
                    p["b"] = t
                p["y"] = y
                # Guardar el recorte más grande del vehículo (mejor oportunidad de leer la placa)
                area = (x2 - x1) * (y2 - y1)
                if tid not in self.multados and area > p.get("area", 0):
                    m = 10
                    p["area"] = area
                    p["recorte"] = frame[max(0, y1 - m):min(h, y2 + m), max(0, x1 - m):min(w, x2 + m)].copy()

                if tid not in self.velocidades and p["a"] is not None and p["b"] is not None:
                    dt = abs(p["b"] - p["a"])
                    if dt > 0.05:
                        kmh = cfg.distancia_m / dt * 3.6
                        self.velocidades[tid] = kmh
                        if kmh > cfg.limite_kmh and tid not in self.multados:
                            self.multados.add(tid)
                            self._pool.submit(self._multar, tid, VEHICULOS.get(cls, "Vehiculo"),
                                              p.pop("recorte"), kmh, replace(cfg))

                kmh = self.velocidades.get(tid)
                if kmh is None:
                    color, texto = (200, 200, 200), f"#{tid}"
                elif kmh > cfg.limite_kmh:
                    color, texto = (0, 0, 255), f"#{tid} {kmh:.0f} km/h EXCESO"
                else:
                    color, texto = (0, 200, 0), f"#{tid} {kmh:.0f} km/h"
                cv2.rectangle(salida, (x1, y1), (x2, y2), color, 2)
                cv2.putText(salida, texto, (x1, max(y1 - 8, 20)), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 0), 4, cv2.LINE_AA)
                cv2.putText(salida, texto, (x1, max(y1 - 8, 20)), cv2.FONT_HERSHEY_SIMPLEX, 0.6, color, 2, cv2.LINE_AA)

        # Olvidar vehículos que ya no se ven
        for tid in [k for k, v in self.pistas.items() if t - v["visto"] > 10]:
            del self.pistas[tid]
        return salida

    def _multar(self, tid, vehiculo, recorte, kmh, cfg):
        try:
            placa, img_placa = self.placas.leer(recorte) if self.placas else ("NO LEGIBLE", None)
            ahora = datetime.now()
            folio = f"{ahora:%Y%m%d-%H%M%S}-{tid}"
            m = Multa(folio=folio, fecha_hora=f"{ahora:%d/%m/%Y %H:%M:%S}", vehiculo=vehiculo,
                      placa=placa, velocidad=kmh, limite=cfg.limite_kmh, exceso=kmh - cfg.limite_kmh,
                      ruta_ticket=str(CARPETA_TICKETS / f"multa_{folio}.png"), img_ticket=None)
            m.img_ticket = dibujar_ticket(m, recorte, img_placa)
            guardar_multa(m)
            with self._lock:
                self._nuevas.append(m)
        except Exception as e:  # no tumbar el hilo del video
            print(f"[radar] error generando multa de #{tid}: {e}")
