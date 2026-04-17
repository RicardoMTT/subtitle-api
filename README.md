# Video Subtitle Pro API 🎥📝

Este proyecto es una API robusta de alto rendimiento desarrollada en **Spring Boot 3** diseñada para automatizar el proceso de transcripción y quemado de subtítulos en videos MP4. La solución utiliza inteligencia artificial (OpenAI Whisper) y herramientas de procesamiento multimedia nativas (FFmpeg).

## 🚀 Características Principales

- **Procesamiento Asíncrono (Polling Pattern):** Implementación de una arquitectura no bloqueante que permite procesar videos largos sin agotar los hilos del servidor ni causar timeouts en el cliente.
- **Optimización de Red:** Extracción automática de audio antes de enviar a Whisper, reduciendo el consumo de ancho de banda y acelerando la transcripción.
- **Estilos Profesionales (ASS):** Soporte completo para el formato Advanced Substation Alpha (.ass), permitiendo animaciones, colores dinámicos y fondos personalizados.
- **Gestión de Jobs:** Rastreo de estado de tareas en tiempo real mediante un sistema de caché (ConcurrentHashMap) con identificadores únicos.
- **Control de Recursos Nativo:** Gestión segura de subprocesos FFmpeg para evitar procesos "zombis" y fugas de CPU.

---

## 🏗️ Arquitectura de Alto Nivel

El flujo de procesamiento sigue el siguiente patrón de diseño:

1.  **Ingesta:** El cliente envía un video MP4. El controlador lo guarda en disco y devuelve un `jobId` inmediatamente (HTTP 202).
2.  **Audio Pipeline:** Se extrae la pista de audio (MP3, 16kHz, mono) para optimizar la subida a la API de OpenAI (Whisper).
3.  **Transcripción:** Se invoca a Whisper con granularidad por palabra para obtener timestamps precisos.
4.  **Generación de Estilos:** Se crea un archivo `.ass` basado en la configuración de estilo recibida (fuentes, colores, márgenes).
5.  **Renderizado (Burn-in):** FFmpeg incrusta los subtítulos permanentemente en el video original usando aceleración de CPU.
6.  **Entrega:** El cliente consulta el estado y descarga el video final cuando el estado es `COMPLETED`.

---

## 🛠️ Requisitos Técnicos

- **Java:** 17 o superior.
- **Framework:** Spring Boot 3.x.
- **Herramientas de Sistema:** - `ffmpeg` y `ffprobe` instalados y configurados en el PATH.
   - Carpeta de fuentes tipográficas personalizada (opcional pero recomendada).
- **API Key:** Cuenta activa en OpenAI para el servicio Whisper.

---

## ⚙️ Configuración (`application.properties`)

```properties
# Configuración de Whisper
whisper.openai-api-key=tu_api_key_aqui
whisper.openai-api-url=[https://api.openai.com/v1/audio/transcriptions](https://api.openai.com/v1/audio/transcriptions)

# Configuración de FFmpeg
subtitle.ffmpeg-path=/usr/bin/ffmpeg
subtitle.ffprobe-path=/usr/bin/ffprobe
subtitle.fonts-dir=/opt/app/fonts
subtitle.temp-dir=/tmp/subtitle-service

# Configuración de Hilos (Async)
async.core-pool-size=2
async.max-pool-size=4