package com.ricardotovart.subtitles_api.service;

import com.ricardotovart.subtitles_api.model.SubtitleConfig;
import com.ricardotovart.subtitles_api.model.TimedWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

    // =========================================================================
    // Pipeline principal (Async)
    // =========================================================================

    /**
     * Procesa un video de forma asíncrona: transcribe + genera ASS + quema subtítulos.
     *
     * @param inputVideo ruta al video original
     * @param language   idioma para Whisper ("es", "en", null = auto)
     * @param config     configuración de estilo de subtítulos
     * @return future con la ruta al video procesado
     */
    @Async("subtitleTaskExecutor")
    public CompletableFuture<Path> processVideo(Path inputVideo, String language, SubtitleConfig config) {

        String jobId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[Job {}] Iniciando procesamiento: {}", jobId, inputVideo.getFileName());

        Path tempDir = null;
        try {
            // Crear directorio temporal para este job
            tempDir = createTempDir(jobId);

            // ── Paso 1: Transcripción con Whisper ──────────────────────────
            log.info("[Job {}] Paso 1/3: Transcribiendo audio con Whisper...", jobId);

            List<TimedWord> words = whisperService.transcribe(inputVideo, language);

            log.info("[Job {}] Transcripción completada: {} palabras ",jobId, words.size() );

            if (words.isEmpty()) {
                throw new RuntimeException("Whisper no detectó palabras en el audio");
            }

            // ── Paso 2: Generar archivo ASS ────────────────────────────────
            // Este archivo será el que se usará para quemar los subtítulos
            log.info("[Job {}] Paso 2/3: Generando subtítulos ASS...", jobId);

            // ─── Generar ASS ───────────────────────────────────────────────
            // Este ASS contendrá los subtítulos para quemar en el video, los subtitulos con animación
            Path assFile = tempDir.resolve("subtitles.ass");

            assGeneratorService.generateAssFile(words, config, assFile);

            log.info("[Job {}] ASS generado ", jobId);

                // ── Paso 3: Quemar subtítulos con FFmpeg ───────────────────────
            log.info("[Job {}] Paso 3/3: Quemando subtítulos en video...", jobId);

            Path outputVideo = tempDir.resolve("output_" + inputVideo.getFileName()); // Se creara una variable que guardara el video final

            ffmpegService.burnSubtitles(inputVideo, assFile, outputVideo,
                    progress -> log.debug("[Job {}] FFmpeg: {}", jobId, progress));


            log.info("[outputVideo {}] Video",
                    outputVideo);
            return CompletableFuture.completedFuture(outputVideo);

        } catch (Exception e) {
            log.error("[Job {}] Error en el procesamiento: {}", jobId, e.getMessage(), e);
//            cleanup(tempDir);
            return CompletableFuture.failedFuture(e);
        }
    }
    // =========================================================================
    // Utilitarios
    // =========================================================================

    private Path createTempDir(String jobId) throws Exception {
        Path base = Paths.get(tempDirPath);
        Files.createDirectories(base);
        Path jobDir = base.resolve(jobId);
        Files.createDirectory(jobDir);
        return jobDir;
    }

}