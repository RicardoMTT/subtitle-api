# Subtitles API

API REST en Java Spring Boot para generar subtítulos automáticos en videos. Utiliza OpenAI Whisper para transcripción, genera subtítulos ASS con estilo karaoke (palabra por palabra) y los quema directamente en el video usando FFmpeg.

---

## Características Principales

- **Transcripción automática**: Usa OpenAI Whisper API para transcribir audio a texto
- **Subtítulos karaoke**: Efecto de palabra por palabra estilo TikTok/Reels
- **Quemado de subtítulos**: Incrusta subtítulos permanentemente en el video
- **Procesamiento asíncrono**: No bloquea peticiones HTTP durante el procesamiento
- **Altamente configurable**: Fuente, colores, tamaño, animaciones, fondo, etc.

---

## Arquitectura

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  SubtitleController  │────▶│ VideoSubtitleService  │────▶│  WhisperService  │
│     (REST API)      │     │   (Orquestador)      │     │ (Transcripción) │
└─────────────────┘     └──────────────────┘     └─────────────────┘
                                 │
                                 ▼
                        ┌──────────────────┐
                        │ AssGeneratorService │
                        │ (Genera .ass)       │
                        └──────────────────┘
                                 │
                                 ▼
                        ┌──────────────────┐
                        │   FFmpegService   │
                        │ (Quema subtítulos)│
                        └──────────────────┘
```

---

## Tecnologías

| Componente | Tecnología |
|------------|------------|
| Framework | Spring Boot 3.3.5 |
| Java | Java 17 |
| Transcripción | OpenAI Whisper API |
| Procesamiento Video | FFmpeg |
| Procesamiento Asíncrono | Spring @Async + ThreadPoolTaskExecutor |
| Web Client | Spring WebFlux WebClient |

---

## Endpoints API

### 1. Procesar Video (Completo)

```http
POST /api/subtitles/process
Content-Type: multipart/form-data
```

**Parámetros:**

| Parámetro | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `video` | File | - | Archivo video MP4 |
| `language` | String | `es` | Idioma (es, en, etc.) |
| `wordsPerLine` | int | `4` | Palabras por línea |
| `fontSize` | int | `72` | Tamaño fuente (px) |
| `highlightFirstWord` | boolean | `true` | Resaltar primera palabra |
| `highlightColor` | String | `&H00FFFFFF` | Color resaltado (BGR) |
| `uppercase` | boolean | `true` | Texto en mayúsculas |
| `playResX` | int | `1080` | Resolución ancho |
| `playResY` | int | `1920` | Resolución alto |
| `backgroundMode` | String | `none` | Fondo: none, box, blur |
| `backgroundColor` | String | `&H00000000` | Color fondo (BGR) |

**Ejemplo cURL:**
```bash
curl -X POST http://localhost:8080/api/subtitles/process \
  -F "video=@mi_video.mp4" \
  -F "language=es" \
  -F "fontSize=72" \
  -F "wordsPerLine=4" \
  --output video_subtitulado.mp4
```

### 2. Procesar Video (Rápido)

```http
POST /api/subtitles/process-quick
Content-Type: multipart/form-data
```

Configuración optimizada para videos verticales (TikTok/Reels).

**Parámetros:**

| Parámetro | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `video` | File | - | Archivo video MP4 |
| `language` | String | `es` | Idioma |

### 3. Health Check

```http
GET /api/subtitles/health
```

**Respuesta:**
```
Subtitle Service OK
```

---

## Estructura del Proyecto

```
src/
├── main/
│   ├── java/com/ricardotovart/subtitles_api/
│   │   ├── SubtitlesApiApplication.java      # Punto de entrada
│   │   ├── config/
│   │   │   └── AsyncConfig.java              # Configuración asíncrona
│   │   ├── controller/
│   │   │   └── SubtitleController.java       # REST endpoints
│   │   ├── exception/
│   │   │   └── ExceptionHandler.java         # Manejo de excepciones
│   │   ├── model/
│   │   │   ├── SubtitleConfig.java           # Configuración subtítulos
│   │   │   └── TimedWord.java                # Palabra con timestamp
│   │   └── service/
│   │       ├── VideoSubtitleService.java     # Orquestador pipeline
│   │       ├── WhisperService.java           # Transcripción Whisper
│   │       ├── AssGeneratorService.java      # Generador ASS
│   │       └── FFmpegService.java            # Procesamiento FFmpeg
│   └── resources/
│       ├── application.yaml                  # Configuración principal
│       └── application.properties            # Configuración adicional
└── test/
    └── java/com/ricardotovart/subtitles_api/
        └── SubtitlesApiApplicationTests.java
```

---

## Configuración

### application.yaml

```yaml
server:
  port: 8080

subtitle:
  # Ruta a FFmpeg (ajustar según sistema operativo)
  ffmpeg-path: /usr/bin/ffmpeg        # Linux/Mac
  # ffmpeg-path: C:/ffmpeg/bin/ffmpeg.exe  # Windows
  
  ffprobe-path: /usr/bin/ffprobe      # Linux/Mac
  # ffprobe-path: C:/ffmpeg/bin/ffprobe.exe # Windows
  
  temp-dir: /tmp/subtitle-service     # Directorio temporal
  processing-timeout-minutes: 30       # Timeout procesamiento

whisper:
  openai-api-key: TU_API_KEY_AQUI      # API Key de OpenAI
  openai-api-url: https://api.openai.com/v1/audio/transcriptions
  model: whisper-1
  use-local: false                     # true para usar Whisper local

async:
  core-pool-size: 2                    # Hilos paralelos base
  max-pool-size: 4                     # Máximo hilos
  queue-capacity: 10                   # Cola de espera

spring:
  servlet:
    multipart:
      max-file-size: 500MB             # Tamaño máximo archivo
      max-request-size: 500MB
```

---

## Requisitos

### Software Requerido

1. **Java 17+**
2. **Maven**
3. **FFmpeg** instalado en el sistema
   - Windows: Descargar de [ffmpeg.org](https://ffmpeg.org/download.html)
   - Linux: `sudo apt install ffmpeg`
   - Mac: `brew install ffmpeg`
4. **API Key de OpenAI** (para Whisper API)

### Dependencias Maven

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- FFmpeg Java wrapper -->
    <dependency>
        <groupId>net.bramp.ffmpeg</groupId>
        <artifactId>ffmpeg</artifactId>
        <version>0.8.0</version>
    </dependency>
    
    <!-- WebClient para llamadas HTTP -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
</dependencies>
```

---

## Instalación y Ejecución

### 1. Clonar y Compilar

```bash
# Clonar repositorio
git clone <url-repositorio>
cd subtitles-api

# Compilar
./mvnw clean package
```

### 2. Configurar

Editar `src/main/resources/application.yaml`:
- Configurar ruta a FFmpeg según tu sistema
- Agregar tu API Key de OpenAI

### 3. Ejecutar

```bash
# Usando Maven
./mvnw spring-boot:run

# O usando el JAR generado
java -jar target/subtitles-api-0.0.1-SNAPSHOT.jar
```

La API estará disponible en: `http://localhost:8080`

---

## Pipeline de Procesamiento

1. **Recepción**: El controller recibe el video vía multipart/form-data
2. **Almacenamiento temporal**: Guarda el archivo en disco temporal
3. **Transcripción**: WhisperService envía audio a OpenAI Whisper API
4. **Generación ASS**: AssGeneratorService crea archivo .ass con estilos
5. **Quemado**: FFmpegService combina video + ASS en video final
6. **Respuesta**: Retorna el video procesado como attachment

---

## Modelos de Datos

### SubtitleConfig

```java
SubtitleConfig config = SubtitleConfig.builder()
    .fontSize(72)                      // Tamaño fuente
    .wordsPerLine(4)                   // Palabras por línea
    .highlightFirstWord(true)          // Resaltar primera palabra
    .highlightColor("&H0000FFFF")      // Amarillo (BGR)
    .uppercase(true)                   // Mayúsculas
    .playRes(1080, 1920)               // Resolución (vertical)
    .backgroundMode("box")             // Fondo tipo caja
    .backgroundColor("&H00000000")       // Negro opaco
    .build();
```

### TimedWord

```java
public class TimedWord {
    private String text;           // Texto de la palabra
    private double startSeconds;   // Tiempo inicio
    private double endSeconds;     // Tiempo fin
    private Double probability;    // Confianza (0-1)
}
```

---

## Formato ASS

El servicio genera archivos ASS (Advanced SubStation Alpha) con:

- **Script Info**: Metadatos del script
- **V4+ Styles**: Estilos de fuente, colores, bordes
- **Events**: Diálogos con timing y efectos karaoke

Formato de color ASS: `&HAABBGGRR`
- AA: Alpha (00=opaco, FF=transparente)
- BB: Azul
- GG: Verde  
- RR: Rojo

---

## Características Técnicas

### Procesamiento Asíncrono

```java
@Async("subtitleTaskExecutor")
public CompletableFuture<Path> processVideo(Path input, String lang, SubtitleConfig config)
```

- Evita bloqueo de threads HTTP de Tomcat
- Permite procesamiento paralelo configurable
- Retorna `CompletableFuture` para manejo no-bloqueante

### Manejo de Archivos Grandes

Para archivos > 25MB, el servicio automáticamente:
1. Extrae solo el audio usando FFmpeg
2. Reduce a 16kHz mono MP3
3. Envía audio comprimido a Whisper API

### Temporales

- Archivos temporales en directorio configurado
- Limpieza automática después de procesamiento
- UUID único por job para aislamiento

---

## Uso de Docker (Opcional)

```dockerfile
FROM openjdk:17-jdk-slim

# Instalar FFmpeg
RUN apt-get update && apt-get install -y ffmpeg

COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

---

## Licencia

Proyecto desarrollado por [Ricardo Tovar](mailto:contacto@ricardotovart.com).

---

## Soporte

Para reportar issues o contribuir, contactar al desarrollador.
