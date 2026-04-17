package com.ricardotovart.subtitles_api.controller;

import com.ricardotovart.subtitles_api.model.JobStatus;
import com.ricardotovart.subtitles_api.model.SubtitleConfig;
import com.ricardotovart.subtitles_api.service.VideoSubtitleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Controller REST para el servicio de subtítulos.
*/
@RestController
@RequestMapping("/api/subtitles")
@CrossOrigin(origins = "*")
public class SubtitleController {
    private static final Logger log = LoggerFactory.getLogger(SubtitleController.class);

    private final VideoSubtitleService videoSubtitleService;

    public SubtitleController(VideoSubtitleService videoSubtitleService) {
        this.videoSubtitleService = videoSubtitleService;
    }

    /**
     * Endpoint principal: recibe video MP4, transcribe con Whisper, agrega subtítulos.
     */
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> processVideo(
            @RequestPart("video") MultipartFile videoFile,
            @RequestParam(value = "language", defaultValue = "es") String language,
            @RequestParam(value = "wordsPerLine", defaultValue = "4") int wordsPerLine,
            @RequestParam(value = "fontSize", defaultValue = "72") int fontSize,
            @RequestParam(value = "highlightFirstWord", defaultValue = "true") boolean highlightFirstWord,
            @RequestParam(value = "highlightColor", defaultValue = "&H00FFFFFF") String highlightColor,
            @RequestParam(value = "uppercase", defaultValue = "true") boolean uppercase,
            @RequestParam(value = "playResX", defaultValue = "1080") int playResX,
            @RequestParam(value = "playResY", defaultValue = "1920") int playResY,
            @RequestParam(value = "backgroundMode", defaultValue = "none") String backgroundMode,
            @RequestParam(value = "backgroundColor", defaultValue = "&H00000000") String backgroundColor,

            // ¡NUEVOS PARÁMETROS AGREGADOS AQUÍ!
            @RequestParam(value = "fontName", defaultValue = "The Bold Font") String fontName,
            @RequestParam(value = "appearMode", defaultValue = "line") String appearMode
    ) {
        log.info("Recibida solicitud: archivo={}, tamaño={}MB, idioma={}",
                videoFile.getOriginalFilename(),
                videoFile.getSize() / 1024 / 1024,
                language);

        String jobId = UUID.randomUUID().toString().substring(0, 8);

        try {
            Path tempInput = Files.createTempFile("input_", "_" + videoFile.getOriginalFilename());
            videoFile.transferTo(tempInput);

            // Agregamos los nuevos parámetros al builder
            SubtitleConfig config = SubtitleConfig.builder()
                    .appearMode(appearMode)     // <-- Inyecta el modo (line o word)
                    .fontSize(fontSize)
                    .wordsPerLine(wordsPerLine)
                    .highlightFirstWord(highlightFirstWord)
                    .highlightColor(highlightColor)
                    .uppercase(uppercase)
                    .playRes(playResX, playResY)
                    .backgroundMode(backgroundMode)
                    .backgroundColor(backgroundColor)
                    .build();

            // Lanza el proceso en background
            videoSubtitleService.processVideoAsync(jobId, tempInput, language, config, videoFile.getOriginalFilename());

            return ResponseEntity.accepted().body(Map.of(
                    "jobId", jobId,
                    "status", "PROCESSING",
                    "message", "El video se está procesando. Consulta el estado usando el jobId."
            ));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error guardando el video original: " + e.getMessage()));
        }
    }

    /**
     * Endpoint para consultar el estado del procesamiento.
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<JobStatus> getJobStatus(@PathVariable String jobId) {
        JobStatus status = videoSubtitleService.getJobStatus(jobId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    /**
     * Endpoint para descargar el video una vez completado.
     */
    @GetMapping("/download/{jobId}")
    public ResponseEntity<Resource> downloadVideo(@PathVariable String jobId) {
        JobStatus status = videoSubtitleService.getJobStatus(jobId);

        if (status == null || !"COMPLETED".equals(status.status())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build(); // O un 404
        }

        try {
            Path videoPath = status.outputPath();
            Resource resource = new FileSystemResource(videoPath.toFile());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"subtitulado_" + videoPath.getFileName().toString() + "\"")
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .contentLength(resource.contentLength())
                    .body(resource);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }


}