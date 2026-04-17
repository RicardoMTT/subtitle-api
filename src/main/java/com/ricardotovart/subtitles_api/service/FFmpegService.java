package com.ricardotovart.subtitles_api.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Servicio que invoca FFmpeg para quemar subtítulos ASS en un video MP4.
 *
 * "Quemar" (hardcode / burn-in) significa incrustar los subtítulos como
 * parte permanente del video. Alternativa a subtítulos "soft" (pista separada).
 *
 * Ventajas de subtítulos quemados:
 *  ✓ Se ven en cualquier reproductor
 *  ✓ Se ven en redes sociales (TikTok, Instagram Reels, YouTube Shorts)
 *  ✓ Compatible con el estilo de la imagen (animaciones ASS visibles)
 *
 */
@Service
public class FFmpegService {

    private static final Logger log = LoggerFactory.getLogger(FFmpegService.class);

    @Value("${subtitle.ffmpeg-path:/usr/bin/ffmpeg}")
    private String ffmpegPath;

    @Value("${subtitle.ffprobe-path:/usr/bin/ffprobe}")
    private String ffprobePath;

    @Value("${subtitle.processing-timeout-minutes:30}")
    private int timeoutMinutes;

    // =========================================================================
    // Quemado de subtítulos
    // =========================================================================

    /**
     * Quema un archivo ASS de subtítulos en un video MP4, quemar significa incrustar los subtítulos como parte permanente del video.
     */
    public void burnSubtitles(Path inputVideo, Path assFile, Path outputVideo,
                              Consumer<String> progressLog) throws Exception {

        log.info("Quemando subtítulos: {} + {} → {}", inputVideo.getFileName(),
                assFile.getFileName(), outputVideo.getFileName());

        // En Windows, las rutas con '\' deben escaparse para el filtro ass=
        // Obtenemos la ruta absoluta del archivo ASS
        String assPath = assFile.toAbsolutePath().toString();

        // Validamos el sistema operativo
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            // FFmpeg en Windows requiere escaping especial en filtros
            assPath = assPath.replace("\\", "\\\\").replace(":", "\\:");
        }

        // Se construye el comando FFmpeg para quemar los subtítulos
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");                          // Sobreescribir output sin preguntar
        command.add("-i");
        command.add(inputVideo.toAbsolutePath().toString());

        // Filtro de video: ass= quema los subtítulos
        // fontsdir permite cargar fuentes personalizadas si las tienes
        command.add("-vf");

        // Aca lo que se hace es agregar el filtro de quemado de subtítulos con el path del archivo ASS
        command.add(String.format("ass='%s'", assPath));

        // Video: re-encodear con H.264 (necesario para aplicar el filtro)
        command.add("-c:v"); command.add("libx264");
        command.add("-preset"); command.add("fast"); // balance velocidad/calidad
        command.add("-crf"); command.add("23");       // 18=alta calidad, 28=más comprimido

        // Audio: copiar sin re-encodear (más rápido, sin pérdida de calidad)
        command.add("-c:a"); command.add("copy");

        // Mantener metadatos del video original
        command.add("-map_metadata"); command.add("0");

        command.add(outputVideo.toAbsolutePath().toString());

        log.debug("Comando FFmpeg: {}", String.join(" ", command));

        executeCommand(command, progressLog);

        log.info("Video generado: {} ({} MB)",
                outputVideo.getFileName(),
                outputVideo.toFile().length() / 1024 / 1024);
    }

    // =========================================================================
    // Ejecución de comandos
    // =========================================================================
    private void executeCommand(List<String> command, Consumer<String> progressLog) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command); // Creamos una instancia de ProcessBuilder que representa el proceso
        pb.redirectErrorStream(true); // FFmpeg escribe su output en stderr


        // Java lanza un proceso nativo en tu computadora. A partir de ese milisegundo, FFmpeg cobra vida, va a tu disco duro,
        // busca el archivo .ass usando la ruta que le pasaste, y empieza a dibujar los subtítulos
        Process process = pb.start();

        // Hilo de lectura para evitar bloqueos del buffer del SO
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[FFmpeg] {}", line);

                    // FFmpeg reporta progreso
                    if (progressLog != null && line.startsWith("frame=")) {
                        progressLog.accept(line);
                    }
                }
            } catch (IOException e) {
                log.error("Error leyendo output de FFmpeg", e);
            }
        });

        outputReader.start();

        try {
            // Espera a que el proceso termine o exceda el timeout
            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);

            // Le damos unos segundos al lector para terminar de imprimir los últimos logs
            outputReader.join(5000);

            if (!finished) {
                // [OPTIMIZACIÓN 3] Matar el proceso si supera el tiempo máximo
                process.destroyForcibly();
                throw new RuntimeException("FFmpeg excedió el timeout de " + timeoutMinutes + " minutos. Proceso destruido.");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg falló con código de salida: " + exitCode);
            }

        } catch (InterruptedException e) {
            // [OPTIMIZACIÓN CRÍTICA] Si el hilo de Spring es cancelado/interrumpido,
            // aseguramos que el proceso nativo de FFmpeg muera y libere la CPU.
            log.error("El hilo de ejecución fue interrumpido. Forzando el cierre de FFmpeg...");
            process.destroyForcibly();

            // Detenemos el hilo de lectura
            outputReader.interrupt();

            // Restauramos el estado de interrupción del hilo actual
            Thread.currentThread().interrupt();

            throw new RuntimeException("La ejecución de FFmpeg fue interrumpida abruptamente", e);
        }
    }
}