import io

import av
import pandas as pd
import streamlit as st
from PIL import Image
from streamlit_webrtc import VideoProcessorBase, webrtc_streamer
from ultralytics import YOLO

from radar import CARPETA_TICKETS, Config, LectorPlacas, Radar

st.set_page_config(page_title="Radar YOLO")
st.title("Radar de velocidad con YOLO")
st.write(
    "Coloca el celular fijo, de lado a la calle. Ajusta las líneas **A** y **B** sobre dos "
    "referencias del piso y escribe la distancia real entre ellas."
)

# ---------------------------------------------------------------- configuración
with st.sidebar:
    st.header("Calibración")
    limite = st.number_input("Límite de velocidad (km/h)", 5, 200, 40, step=5)
    distancia = st.number_input("Distancia real entre A y B (m)", 1.0, 100.0, 10.0, step=0.5)
    sentido = st.radio("Movimiento de los autos en pantalla",
                       ["Izquierda a derecha (líneas verticales)", "Arriba a abajo (líneas horizontales)"])
    vertical = sentido.startswith("Izquierda")
    eje = "ancho" if vertical else "altura"
    linea_a, linea_b = st.slider(f"Posición de las líneas A y B (% del {eje})", 5, 95, (30, 70))
    conf = st.slider("Confianza mínima de YOLO", 0.1, 0.9, 0.4, step=0.05)

cfg = Config(limite_kmh=limite, distancia_m=distancia, linea_a=linea_a / 100,
             linea_b=linea_b / 100, conf=conf, vertical=vertical)


@st.cache_resource(show_spinner="Cargando detector de placas y OCR...")
def cargar_lector():
    return LectorPlacas()


lector = cargar_lector()


class RadarProcessor(VideoProcessorBase):
    def __init__(self):
        # Cada sesión de cámara tiene su propio YOLO para que el tracker no se mezcle
        self.radar = Radar(YOLO("yolo11n.pt"), lector, cfg)

    def recv(self, frame):
        img = frame.to_ndarray(format="bgr24")
        return av.VideoFrame.from_ndarray(self.radar.procesar(img), format="bgr24")


ctx = webrtc_streamer(
    key="radar",
    video_processor_factory=RadarProcessor,
    media_stream_constraints={"video": {"facingMode": "environment"}, "audio": False},
    async_processing=True,
)
if ctx.video_processor:
    ctx.video_processor.radar.cfg = cfg

# ---------------------------------------------------------------- alertas
st.session_state.setdefault("multas", [])


@st.fragment(run_every=1)
def panel_multas():
    if ctx.video_processor:
        for m in ctx.video_processor.radar.obtener_multas():
            st.session_state.multas.insert(0, m)
            st.toast(f"Exceso de velocidad - Placa {m.placa}: {m.velocidad:.0f} km/h")

    multas = st.session_state.multas
    if not multas:
        st.info("Sin infracciones por ahora.")
        return

    m = multas[0]
    st.error(
        f"**EXCESO DE VELOCIDAD** — Placa **{m.placa}** — "
        f"{m.velocidad:.1f} km/h (límite {m.limite:.0f}, +{m.exceso:.1f})"
    )
    st.image(m.img_ticket, caption=f"Folio {m.folio}")
    buf = io.BytesIO()
    Image.fromarray(m.img_ticket).save(buf, format="PNG")
    st.download_button("Descargar ticket", buf.getvalue(), f"multa_{m.folio}.png",
                       "image/png", key=f"dl_{m.folio}")


st.subheader("Multas")
panel_multas()

csv_path = CARPETA_TICKETS / "multas.csv"
if csv_path.exists():
    with st.expander("Historial de multas"):
        st.dataframe(pd.read_csv(csv_path).iloc[::-1], hide_index=True)
