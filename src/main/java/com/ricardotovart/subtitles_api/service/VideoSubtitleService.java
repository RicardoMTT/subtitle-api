package com.ricardotovart.subtitles_api.service;

import com.ricardotovart.subtitles_api.model.JobStatus;
import com.ricardotovart.subtitles_api.model.SubtitleConfig;
import com.ricardotovart.subtitles_api.model.TimedWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquestador del pipeline completo:
 *
 *   1. Recibe video MP4
 *   2. Transcribe con WhisperService → List<TimedWord>
 *   3. Genera archivo .ass con AssGeneratorService
 *   4. Quema subtítulos con FFmpegService
 *   5. Retorna video procesado
 *
 * El método principal es @Async para no bloquear el hilo HTTP.
 * El resultado se obtiene via CompletableFuture desde el controller.
 */
@Service
public class VideoSubtitleService {

    // Almacenamiento en caché local (hilo-seguro)
    private final Map<String, JobStatus> jobCache = new ConcurrentHashMap<>();

    private static final Logger log = LoggerFactory.getLogger(VideoSubtitleService.class);

    private final WhisperService whisperService;
    private final AssGeneratorService assGeneratorService;
    private final FFmpegService ffmpegService;

    @Value("${subtitle.temp-dir:/tmp/subtitle-service}")
    private String tempDirPath;

    public VideoSubtitleService(WhisperService whisperService,
                                AssGeneratorService assGeneratorService,
                                FFmpegService ffmpegService) {
        this.whisperService = whisperService;
        this.assGeneratorService = assGeneratorService;
        this.ffmpegService = ffmpegService;
    }


    /**
     * Consulta el estado de un trabajo.
     */
    public JobStatus getJobStatus(String jobId) {
        return jobCache.get(jobId);
    }

    /**
     * Proceso principal asíncrono (No bloquea al Controller).
     * Este @Async es el que hace que el método se ejecute en un hilo separado.
     * El valor subtitleTaskExecutor es el nombre del executor de hilos configurado en AsyncConfig.
     */
    @Async("subtitleTaskExecutor")
    public void processVideoAsync(String jobId, Path inputVideo, String language, SubtitleConfig config, String originalFilename) {
        log.info("[Job {}] Iniciando procesamiento asíncrono: {}", jobId, originalFilename);

        // Todo este bloque de código (Whisper, ASS, FFmpeg)
        // no correrá en el hilo principal de Tomcat,
        // sino en uno de los 4 a 8 hilos que configuraste en tu AsyncConfig.


        // Registrar el estado inicial
        jobCache.put(jobId, JobStatus.processing(jobId));
        Path tempDir = null;

        try {
            tempDir = createTempDir(jobId);

            // ── Paso 1: Transcripción con Whisper
            log.info("[Job {}] Paso 1/3: Transcribiendo audio...", jobId);
            List<TimedWord> words = whisperService.transcribe(inputVideo, language);

            if (words.isEmpty()) {
                throw new RuntimeException("Whisper no detectó palabras en el audio");
            }

            // ── Paso 2: Generar archivo ASS
            log.info("[Job {}] Paso 2/3: Generando subtítulos ASS...", jobId);
            Path assFile = tempDir.resolve("subtitles.ass");
            assGeneratorService.generateAssFile(words, config, assFile);

            // ── Paso 3: Quemar subtítulos con FFmpeg
            log.info("[Job {}] Paso 3/3: Quemando subtítulos en video...", jobId);
            Path outputVideo = tempDir.resolve("output_" + inputVideo.getFileName().toString());

            ffmpegService.burnSubtitles(inputVideo, assFile, outputVideo,
                    progress -> log.debug("[Job {}] FFmpeg progreso: {}", jobId, progress));

            log.info("[Job {}] Procesamiento exitoso. Archivo listo en: {}", jobId, outputVideo);

            // Actualizar caché a COMPLETADO
            jobCache.put(jobId, JobStatus.completed(jobId, outputVideo));

        } catch (Exception e) {
            log.error("[Job {}] Error crítico en el procesamiento: {}", jobId, e.getMessage(), e);
            // Actualizar caché a FALLIDO
            jobCache.put(jobId, JobStatus.failed(jobId, e.getMessage()));
        } finally {
            // Limpieza: Borramos el video original de entrada para no llenar el disco,
            // ya que ya lo usamos. (NO borramos el outputVideo aún, el usuario debe descargarlo)
            try {
                Files.deleteIfExists(inputVideo);
            } catch (IOException ignored) {
                log.warn("[Job {}] No se pudo eliminar el archivo temporal de entrada", jobId);
            }
        }
    }

    private Path createTempDir(String jobId) throws Exception {
        Path base = Paths.get(tempDirPath);
        Files.createDirectories(base);
        Path jobDir = base.resolve(jobId);
        if (!Files.exists(jobDir)) {
            Files.createDirectory(jobDir);
        }
        return jobDir;
    }

}