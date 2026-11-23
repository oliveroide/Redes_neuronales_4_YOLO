# Resumen del chat con Claude (para continuar en otra sesión)

Este archivo resume una conversación con Claude Code sobre este proyecto. Para seguir en otra sesión, adjunta este archivo y di: *"Lee CHAT_CLAUDE.md y continúa desde ahí"*.

## Estado actual del proyecto

- **Versión principal: Streamlit** (en la raíz). `app.py` + `radar.py` corren en la laptop. El celular abre la página por un túnel de Cloudflare y manda su cámara por WebRTC.
- **Versión Android** (en `android/`): app nativa en Kotlin con YOLO11 en TFLite. Compila e instala en el Pixel, pero no se confirmó que funcione bien en la calle, así que quedó como experimental.
- **Presentación escolar**: 12 diapositivas sobre la versión Streamlit, en un artifact de claude.ai (Slides):
  https://claude.ai/artifact/Ss9RHYU4JrfsejkTcZVcdx (privado; para que otros la vean hay que compartirla desde el menú Share).

## Cómo correr la versión Streamlit

1. Conecta la laptop al hotspot del celular, para que celular y laptop queden en la misma red.
2. En la raíz del repo, con el entorno activado:
   ```powershell
   .\.venv\Scripts\Activate.ps1
   streamlit run app.py
   ```
3. En otra terminal:
   ```powershell
   cloudflared tunnel --url http://localhost:8501
   ```
4. Abre en el celular la URL `https://….trycloudflare.com`, permite la cámara y presiona **START**.

Si el puerto 8501 está ocupado (por ejemplo, por otro proyecto de Streamlit), usa `--server.port 8510` y apunta el túnel a ese puerto.

## Cambios hechos en este chat

- `app.py`: se agregó un servidor STUN en `webrtc_streamer` (`rtc_configuration`) para que el video WebRTC encuentre la ruta del celular a la laptop.
- `README.md`: la sección del celular usa Cloudflare Quick Tunnel en vez de ngrok (`streamlit run app.py` + `cloudflared tunnel --url http://localhost:8501`).
- Los archivos de Streamlit volvieron a la raíz. Habían estado un tiempo en `legacy_streamlit/`.
- `android/README.md`: instrucciones de la app Android (abrir `android/` en Android Studio, activar depuración USB en el Pixel y presionar Run).
- `.gitignore`: se agregaron las carpetas de compilación de Android y `.idea/`.
- Se creó y luego se borró un script `iniciar.ps1`. El usuario prefiere correr los comandos a mano.

## Historial de la conversación (resumido)

1. **"Ya tengo Android Studio, ¿cómo corro la app en mi Pixel?"**: activar las opciones de desarrollador y la depuración USB, abrir la carpeta `android/` y presionar Run. Los modelos `.tflite` ya estaban en `assets`.
2. **"Si lo desconecto, ¿ya no lo puedo probar en la calle?"**: sí se puede. La app queda instalada y funciona sin internet, porque ML Kit OCR viene incluido.
3. **"¿Cómo funciona la app?"**: se explicaron la calibración (límite, distancia, líneas A/B, orientación, confianza) y el flujo YOLO → tracker → cruce de líneas → km/h → placa → ticket.
4. **"Haz una presentación con la skill de slides"**: el usuario pidió orden técnico primero, unos 10 minutos para la clase y estilo claro y académico.
5. **"No supe si funcionó, mejor regresa a Streamlit con la laptop y Cloudflare"**: se regresaron los archivos a la raíz, se cambió ngrok por Cloudflare y se probó que la app carga en localhost y por el túnel.
6. **"Cambia la presentación"**: se reescribió para la versión Streamlit (WebRTC, ByteTrack, EasyOCR, Cloudflare, código de `radar.py`).
7. **"Quita del README el iniciar.ps1"**: el README quedó con `streamlit run app.py` y el comando de `cloudflared`, y se borró el script.
8. **"Haz commit y push con el archivo del chat"**: se creó este archivo y se subió todo a `main`.

## Pendientes / ideas

- En la portada de la presentación faltan el nombre del alumno, el de la maestra y la fecha.
- Probar el radar en la calle y ajustar la calibración (medir bien la distancia entre A y B).
- Opcional: confirmar si la app Android funciona en el Pixel o dejarla como prototipo.
