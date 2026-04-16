package com.ricardotovart.subtitles_api.controller;

import com.ricardotovart.subtitles_api.model.SubtitleConfig;
import com.ricardotovart.subtitles_api.service.VideoSubtitleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    public CompletableFuture<ResponseEntity<Resource>> processVideo(
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
            @RequestParam(value = "backgroundColor", defaultValue = "&H00000000") String backgroundColor
    ) {
        log.info("Recibida solicitud: archivo={}, tamaño={}MB, idioma={}",
                videoFile.getOriginalFilename(),
                videoFile.getSize() / 1024 / 1024,
                language);

        Path tempInput = null;
        try {
            // Guardar video que subiste en el archivo temporal tempInput
            tempInput = Files.createTempFile("input_", "_" + videoFile.getOriginalFilename() );
            videoFile.transferTo(tempInput);

            // Construir configuración necesaria para el procesamiento, estas configuraciones se agregan al video
            SubtitleConfig config = SubtitleConfig.builder()
                    .fontSize(fontSize)
                    .wordsPerLine(wordsPerLine)
                    .highlightFirstWord(highlightFirstWord)
                    .highlightColor(highlightColor)
                    .uppercase(uppercase)
                    .playRes(playResX, playResY)
                    .backgroundMode(backgroundMode)
                    .backgroundColor(backgroundColor)
                    .build();

            // Guardar video que subiste en el archivo temporal tempInput en la variable finalTempInput
            final Path finalTempInput = tempInput;

            System.out.println(
                    "finalTempInput: " + finalTempInput.getFileName()
            );


            // Lanzar procesamiento asíncrono
            return videoSubtitleService.processVideo(finalTempInput, language, config)
                    .thenApply(outputPath -> buildFileResponse(outputPath, videoFile.getOriginalFilename()))
                    .whenComplete((response, error) -> {
                        System.out.println("error" + error);
                        System.out.println("response" + response);
                        System.out.println("finalTempInput: " + finalTempInput.getFileName());
                        // Limpiar input temporal
                        try { Files.deleteIfExists(finalTempInput); } catch (IOException ignored) {}

                        if (error != null) {
                            log.error("Error procesando video: {}", error.getMessage());
                        }
                    });

        } catch (IOException e) {
            log.error("Error guardando video temporal", e);
            return CompletableFuture.completedFuture(
                    ResponseEntity.internalServerError().build());
        }
    }

    /**
     * Endpoint simplificado para uso rápido.
     * Usa configuración por defecto para videos verticales de redes sociales.
     */
    @PostMapping(value = "/process-quick", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<Resource>> processVideoQuick(
            @RequestPart("video") MultipartFile videoFile,
            @RequestParam(value = "language", defaultValue = "es") String language
    ) {
        // Config por defecto: optimizada para TikTok/Reels (1080x1920)
        SubtitleConfig config = SubtitleConfig.builder()
                .fontSize(72)
                .wordsPerLine(4)
                .highlightFirstWord(true)
                .highlightColor("&H0000FFFF") // Amarillo: karaoke activo
                .uppercase(true)
                .playRes(1080, 1920)
                .outlineWidth(4)
                .marginBottom(100)
                // Fondo negro sólido + karaoke amarillo (estilo de la imagen)
                .backgroundMode("box")
                .backgroundColor("&H00000000")
                .build();

        try {
            Path tempInput = Files.createTempFile("input_quick_", ".mp4");
            videoFile.transferTo(tempInput);

            return videoSubtitleService.processVideo(tempInput, language, config)
                    .thenApply(out -> buildFileResponse(out, videoFile.getOriginalFilename()))
                    .whenComplete((r, e) -> {
                        try { Files.deleteIfExists(tempInput); } catch (IOException ignored) {}
                    });

        } catch (IOException e) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.internalServerError().build());
        }
    }

    // =========================================================================
    // Health check
    // =========================================================================

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Subtitle Service OK");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    // Metodo privado usado para construir la respuesta de un archivo
    private ResponseEntity<Resource> buildFileResponse(Path outputPath, String originalName) {
        Resource resource = new FileSystemResource(outputPath);

        String outputFilename = "subtitled_" + (originalName != null ? originalName : "output.mp4");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + outputFilename + "\"")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(resource);
    }
}