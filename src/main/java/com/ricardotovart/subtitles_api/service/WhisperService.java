package com.ricardotovart.subtitles_api.service;


import com.ricardotovart.subtitles_api.model.TimedWord;
import org.slf4j.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de transcripción automática usando OpenAI Whisper.
 *
 * Soporta dos modos:
 *  - API de OpenAI (whisper-1): rápido, requiere API key, costo por minuto de audio.
 *  - Whisper local (Python):    gratuito, requiere GPU/CPU potente, más lento.
 *
 * Configuración en application.yml:
 *   whisper.use-local=false → usa API OpenAI
 *   whisper.use-local=true  → usa proceso Python local
 */
@Service
public class WhisperService {

    private static final Logger log = LoggerFactory.getLogger(WhisperService.class);

    @Value("${whisper.openai-api-key}")
    private String apiKey;

    @Value("${whisper.openai-api-url}")
    private String apiUrl;

    @Value("${whisper.model:whisper-1}")
    private String model;

    @Value("${whisper.use-local:false}")
    private boolean useLocal;

    @Value("${whisper.local-python-path:python3}")
    private String pythonPath;

    @Value("${whisper.local-model-size:medium}")
    private String localModelSize;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    public WhisperService() {
        this.webClient = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(100 * 1024 * 1024)) // 100MB
                .build();
    }

    /**
     * Transcribe un archivo de audio/video y retorna lista de palabras con timestamps.
     *
     * @param mediaFile archivo MP4, MP3, WAV, etc.
     * @param language  código de idioma ISO 639-1 (ej: "es", "en"). null = auto-detect.
     * @return lista de palabras con timestamps
     */
    public List<TimedWord> transcribe(Path mediaFile, String language) throws Exception {
        log.info("Iniciando transcripción de: {} [modo={}]", mediaFile.getFileName(), useLocal ? "local" : "api");
        return transcribeViaApi(mediaFile, language);

    }


    /**
     * Transcripción via API de OpenAI.
     */
    private List<TimedWord> transcribeViaApi(Path mediaFile, String language) throws Exception {
        log.debug("Llamando a Whisper API para: {}", mediaFile);

        // Para archivos > 25MB, extraer solo el audio (mucho más pequeño)
        Path audioFile = mediaFile;
        boolean audioExtracted = false;

        // Valida si el archivo es mayor a 24MB
        if (mediaFile.toFile().length() > 24 * 1024 * 1024L) {
            System.out.println("El archivo es menor ");
            log.info("Archivo grande ({}MB), extrayendo audio...",
                    mediaFile.toFile().length() / 1024 / 1024);
            audioFile = extractAudioForWhisper(mediaFile);
            audioExtracted = true;
        }

        try {
            // Crea el body de la petición de tipo multipart
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("file", new FileSystemResource(audioFile.toFile()));
            bodyBuilder.part("model", model);
            bodyBuilder.part("response_format", "verbose_json");  // Para obtener timestamps
            bodyBuilder.part("timestamp_granularities[]", "word"); // Timestamps por palabra

            if (language != null && !language.isBlank()) {
                bodyBuilder.part("language", language);
            }

            // Crea el body de la petición
            MultiValueMap<String, HttpEntity<?>> body = bodyBuilder.build();

            String response = webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMinutes(10))
                    .block();
            System.out.println("Respuesta de Whisper API: " + response);
            return parseWhisperVerboseJson(response);

        } finally {
            if (audioExtracted) {
                audioFile.toFile().delete();
                log.debug("Audio temporal eliminado: {}", audioFile);
            }
        }
    }

    /**
     * Parsea la respuesta verbose_json de Whisper API.
     *
     * Estructura de respuesta:
     * {
     *   "text": "transcripción completa",
     *   "words": [
     *     { "word": "Hola", "start": 0.0, "end": 0.5 },
     *     { "word": "mundo", "start": 0.5, "end": 1.2 }
     *   ]
     * }
     */
    private List<TimedWord> parseWhisperVerboseJson(String json) throws Exception {
        List<TimedWord> words = new ArrayList<>();


        JsonNode root = objectMapper.readTree(json);
        JsonNode wordsNode = root.get("words");

        if (wordsNode == null || !wordsNode.isArray()) {
            // Fallback: si no hay timestamps por palabra, usar segmentos
            System.out.println("No se encontraron timestamps por palabra. Usando segmentos...");
            return new ArrayList<>();
        }

        for (JsonNode wordNode : wordsNode) {
            String text = wordNode.get("word").asText().trim();
            double start = wordNode.get("start").asDouble();
            double end = wordNode.get("end").asDouble();

            if (!text.isEmpty()) {
                TimedWord word = new TimedWord(text, start, end);

                // Probabilidad de confianza (opcional)
                if (wordNode.has("probability")) {
                    word.setProbability(wordNode.get("probability").asDouble());
                }

                words.add(word);
            }
        }

        log.info("Transcripción completada: {} palabras detectadas", words.size());
        return words;
    }



    // =========================================================================
    // Utilitario: extraer audio de video para reducir tamaño
    // =========================================================================

    /**
     * Extrae solo el audio de un video MP4.
     * Un video de 100MB suele tener audio de solo 3-5MB.
     * Útil para superar el límite de 25MB de la API de OpenAI.
     */
    private Path extractAudioForWhisper(Path videoFile) throws Exception {
        Path audioOutput = videoFile.getParent()
                .resolve(videoFile.getFileName().toString().replace(".mp4", "_audio.mp3"));

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", videoFile.toString(),
                "-vn",                    // Sin video
                "-acodec", "mp3",
                "-ar", "16000",           // 16kHz suficiente para Whisper
                "-ac", "1",               // Mono
                "-ab", "64k",             // Bitrate bajo (solo necesitamos voz)
                audioOutput.toString()
        );

        pb.redirectErrorStream(true);
        Process proc = pb.start();

        // Consumir output para no bloquear el proceso
        try (InputStream is = proc.getInputStream()) {
            is.transferTo(OutputStream.nullOutputStream());
        }

        int exitCode = proc.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg no pudo extraer el audio");
        }

        log.info("Audio extraído: {} ({} KB)",
                audioOutput.getFileName(),
                audioOutput.toFile().length() / 1024);

        return audioOutput;
    }

}