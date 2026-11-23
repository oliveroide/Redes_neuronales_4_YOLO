# Radar de velocidad con YOLO

App web para el celular que usa **YOLO11** para detectar vehículos con la cámara, estimar su velocidad y, si rebasan el límite, generar una **multa simulada** con el número de placa.

## Cómo funciona

1. **Detección y seguimiento:** `yolo11n.pt` (preentrenado en COCO) detecta autos, motos, autobuses y camiones. ByteTrack le asigna un ID a cada vehículo entre cuadros.
2. **Velocidad:** en pantalla hay dos líneas, **A** y **B**, separadas por una distancia real conocida _d_ (metros). Son verticales si los autos cruzan de izquierda a derecha (cámara de lado a la calle) u horizontales si se acercan o se alejan. Se guarda el instante en que cada vehículo (el centro de su caja o su punto de contacto con el piso) cruza cada línea:

   $$v\,[\text{km/h}] = \frac{d}{t_B - t_A} \times 3.6$$

   Se usa el reloj real y no el número de cuadro, porque los FPS de la cámara por WebRTC cambian.

3. **Placa:** cuando hay exceso, el recorte del vehículo pasa por un detector YOLO de placas ([`morsetechlab/yolov11-license-plate-detection`](https://huggingface.co/morsetechlab/yolov11-license-plate-detection)) y la placa se lee con **EasyOCR**. Si no se encuentra, se intenta OCR en la parte baja del vehículo; si tampoco funciona, se marca como `NO LEGIBLE`. Esto corre en otro hilo para que el video no se congele.
4. **Ticket:** aparece una alerta en la app con la boleta (folio, fecha, placa, velocidad, límite, exceso y fotos). Se puede descargar y también se guarda en `tickets/` junto con `tickets/multas.csv`.

## Estructura

```
├── app.py            # Interfaz Streamlit con cámara en vivo (streamlit-webrtc)
├── radar.py          # Tracking, velocidad, lectura de placa y ticket
├── requirements.txt
├── tickets/          # Multas generadas (PNG + CSV)
└── android/          # Versión experimental como app nativa Android (ver android/README.md)
```

## Instalación

```bash
python -m venv .venv
.venv\Scripts\activate          # Windows  (Linux/Mac: source .venv/bin/activate)
pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
pip install -r requirements.txt
streamlit run app.py
```

La primera vez se descargan solos `yolo11n.pt`, el detector de placas y los pesos de EasyOCR.

## Usarlo desde el celular

El navegador del celular solo da acceso a la cámara (`getUserMedia`) por **HTTPS**. el puerto local se publica con un túnel. Aquí se usa un **Cloudflare Quick Tunnel**, que no pide cuenta ni token: solo hay que instalar [`cloudflared`](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/).

1. Conecta la laptop al **hotspot del celular**. El túnel solo transporta la página. El video viaja por WebRTC directamente del celular a la laptop, y eso funciona mejor si los dos están en la misma red.
2. En la laptop, desde la raíz del repo y con el `.venv` activado, arranca la app:

   ```bash
   streamlit run app.py
   ```

3. En otra terminal, abre el túnel hacia el puerto de Streamlit:

   ```bash
   cloudflared tunnel --url http://localhost:8501
   ```

   Espera la línea con `https://<algo>.trycloudflare.com`.

4. En el celular, abre esa URL en Chrome, permite la cámara y presiona **START**.

La URL cambia cada vez que inicias el túnel. Para cerrar, presiona `Ctrl+C` en las dos terminales.

## Relación con lo visto en clase

| Tema                                       | Módulo | Cómo aparece en este proyecto                                                                                                                                                                                                        |
| ------------------------------------------ | ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Inferencia directa con modelo preentrenado | 13     | `yolo11n.pt` viene entrenado en COCO (80 clases). "Auto", "moto", "autobús" y "camión" ya están en su vocabulario, así que se usa sin reentrenar, igual que VGG16/ResNet50 con clases de ImageNet.                                   |
| Transfer learning / fine-tuning            | 14     | El detector de placas es YOLO11 **ajustado (fine-tuning)** sobre ~10 mil imágenes de placas: se reutiliza el extractor de características y se adapta el cabezal a una sola clase (`License_Plate`).                                 |
| Supuesto de mundo cerrado                  | 13–14  | YOLO solo conoce sus clases; un objeto que no está en COCO puede detectarse como la clase "más parecida". Por eso solo se aceptan las clases de vehículos y hay un umbral de confianza.                                              |
| Preprocesamiento consistente en inferencia | 11     | No se normaliza nada a mano: Ultralytics hace internamente el _letterbox_ y la escala de los píxeles, igual que `Rescaling` dentro del modelo. Al detector de placas no se le agrandan recortes chicos a 640 px para no deformarlos. |
| Matriz de confusión y umbral de decisión   |
| Despliegue web y HTTPS con un túnel        | 12     | La app corre en el navegador del celular y se publica por HTTPS con `cloudflared tunnel --url http://localhost:8501`.                                                                                                                |

## Errores del radar

Si la clase positiva es "va con exceso":

- **Falso positivo (FP):** se multa a alguien que no iba rápido. Es el error **más costoso**, así que conviene priorizar la **Precision**: subir la confianza mínima de YOLO, calibrar bien la distancia y no poner el límite justo en la velocidad típica de la calle.
- **Falso negativo (FN):** se escapa un auto con exceso (por ejemplo, el tracker pierde su ID entre A y B). Baja el **Recall**, pero no perjudica a nadie.

El umbral de confianza subirlo reduce los FP y aumenta los FN.

**Métricas publicadas del detector de placas** (`v1n`, según su ficha en Hugging Face): Precision 0.989, Recall 0.951, mAP@50 0.981. Los mismos autores advierten que el dataset de origen tiene **fuga de datos entre train y test** (las mismas imágenes en ambos conjuntos), así que esas cifras están **infladas**.

## Calibración

- Celular **fijo** (tripié o apoyado), viendo la calle de lado o en diagonal.
- Elige dos referencias en el piso (postes, líneas, juntas de banqueta) y mide la distancia real entre ellas.
- Mueve las líneas A y B en la barra lateral hasta que queden sobre esas referencias y escribe la distancia.

## Limitaciones

- Es una **estimación**: depende de la calibración y de que la cámara no se mueva.
- Elige en la barra lateral el sentido correcto del movimiento. Con la cámara de lado, la medición es más precisa cuando la calle está perpendicular a la vista.
- Las placas lejanas o borrosas pueden salir `NO LEGIBLE`: con un video 4K de autopista, las placas medían ~15 px de alto. El celular debe quedar cerca de la calle. El OCR está entrenado en inglés, lo que funciona para placas mexicanas (letras y números).
