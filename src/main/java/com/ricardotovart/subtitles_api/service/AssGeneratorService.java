package com.ricardotovart.subtitles_api.service;

import com.ricardotovart.subtitles_api.model.SubtitleConfig;
import com.ricardotovart.subtitles_api.model.TimedWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class AssGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AssGeneratorService.class);

    public void generateAssFile(List<TimedWord> words, SubtitleConfig config, Path output)
            throws IOException {

        log.info("Generando ASS: {} palabras, {}/linea, modo={}, fondo={}",
                words.size(), config.getWordsPerLine(),
                config.getAppearMode(), config.getBackgroundMode());

        StringBuilder ass = new StringBuilder();
        ass.append(buildScriptInfo(config));
        ass.append(buildStyles(config));
        ass.append(buildEvents(words, config));

        Files.writeString(output, ass.toString(), StandardCharsets.UTF_8);
        log.info("ASS generado: {} ({} bytes)", output, Files.size(output));
    }

    // =========================================================================
    // Script Info
    // =========================================================================

    private String buildScriptInfo(SubtitleConfig config) {
        return "[Script Info]\r\n"
                + "ScriptType: v4.00+\r\n"
                + "WrapStyle: 0\r\n"
                + "ScaledBorderAndShadow: yes\r\n"
                + "PlayResX: " + config.getPlayResX() + "\r\n"
                + "PlayResY: " + config.getPlayResY() + "\r\n"
                + "\r\n";
    }

    // =========================================================================
    // Estilos
    // =========================================================================

    private String buildStyles(SubtitleConfig config) {
        String fmt = "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, "
                + "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, "
                + "ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
                + "Alignment, MarginL, MarginR, MarginV, Encoding\r\n";

        boolean hasBg     = config.hasBackground();
        int borderStyle   = hasBg ? 3 : 1;
        int outline       = hasBg ? 0 : config.getOutlineWidth();
        int shadow        = hasBg ? 0 : config.getShadow();
        int spacing       = hasBg ? 8 : 0;
        String backColour = hasBg ? config.getBackgroundColor() : "&H00000000";
        String hlColor    = toAssFullColor(config.getHighlightColor());

        String style = "Style: Sub,"
                + config.getFontName() + ","
                + config.getFontSize() + ","
                + hlColor + ","
                + "&H00FFFFFF,"
                + config.getOutlineColor() + ","
                + backColour + ","
                + "1,0,0,0,100,100," + spacing + ",0,"
                + borderStyle + "," + outline + "," + shadow + ","
                + config.getAlignment() + ","
                + config.getMarginSide() + ","
                + config.getMarginSide() + ","
                + config.getMarginBottom() + ",1\r\n";

        return "[V4+ Styles]\r\n" + fmt + style + "\r\n";
    }

    // Aca se crean los eventos de los subtítulos como dialogos
    private String buildEvents(List<TimedWord> words, SubtitleConfig config) {
        StringBuilder events = new StringBuilder();
        events.append("[Events]\r\n");
        events.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\r\n");

        List<List<TimedWord>> lines = groupWordsIntoLines(words, config.getWordsPerLine());

        if (config.isWordByWord()) {
            buildWordByWordEvents(lines, config, events);
        } else {
            buildLineEvents(lines, config, events);
        }

        return events.toString();
    }

    // =========================================================================
    // Modo "line": un dialogo por linea, sin highlight
    // =========================================================================

    private void buildLineEvents(List<List<TimedWord>> lines, SubtitleConfig config,
                                 StringBuilder events) {
        for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
            List<TimedWord> line = lines.get(lineIdx);
            if (line.isEmpty()) continue;

            long lineStart     = line.get(0).getStartMs();
            long lastWordEnd   = line.get(line.size() - 1).getEndMs();
            long nextLineStart = (lineIdx + 1 < lines.size() && !lines.get(lineIdx + 1).isEmpty())
                    ? lines.get(lineIdx + 1).get(0).getStartMs()
                    : Long.MAX_VALUE;
            long lineEnd = Math.min(lastWordEnd, nextLineStart);

            if (lineStart >= lineEnd) continue;

            events.append("Dialogue: 0,")
                    .append(formatAssTime(lineStart)).append(",")
                    .append(formatAssTime(lineEnd)).append(",")
                    .append("Sub,,0,0,0,,")
                    .append(buildWhiteText(line, config))
                    .append("\r\n");
        }
    }

    // =========================================================================
    // Modo "word": palabras aparecen una a una acumulandose
    // =========================================================================

    private void buildWordByWordEvents(List<List<TimedWord>> lines, SubtitleConfig config,
                                       StringBuilder events) {
        String hlBgr = extractBgr(config.getHighlightColor());

        for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
            List<TimedWord> line = lines.get(lineIdx);
            if (line.isEmpty()) continue;

            long lastWordEnd   = line.get(line.size() - 1).getEndMs();
            long nextLineStart = (lineIdx + 1 < lines.size() && !lines.get(lineIdx + 1).isEmpty())
                    ? lines.get(lineIdx + 1).get(0).getStartMs()
                    : Long.MAX_VALUE;
            long lineEnd = Math.min(lastWordEnd, nextLineStart);

            for (int i = 0; i < line.size(); i++) {
                long wordStart = line.get(i).getStartMs();
                long wordEnd   = (i < line.size() - 1)
                        ? line.get(i).getEndMs()
                        : lineEnd;
                wordEnd = Math.min(wordEnd, nextLineStart);

                if (wordStart >= wordEnd) continue;

                StringBuilder text = new StringBuilder();
                for (int j = 0; j <= i; j++) {
                    String w = wordText(line.get(j), config);
                    // si es la última palabra, no se pone el espacio
                    if (j < i) {
                        text.append("{\\1c&HFFFFFF&}").append(w)
                                .append("{\\1c&H").append(hlBgr).append("&} ");
                    } else {
                        text.append(w);
                    }
                }

                events.append("Dialogue: 0,")
                        .append(formatAssTime(wordStart)).append(",")
                        .append(formatAssTime(wordEnd)).append(",")
                        .append("Sub,,0,0,0,,")
                        .append(text)
                        .append("\r\n");
            }
        }
    }

    // =========================================================================
    // Helpers de texto
    // =========================================================================

    private String buildWhiteText(List<TimedWord> line, SubtitleConfig config) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < line.size(); j++) {
            sb.append(wordText(line.get(j), config));
            if (j < line.size() - 1) sb.append(" ");
        }
        return sb.toString();
    }

    private String wordText(TimedWord word, SubtitleConfig config) {
        return config.isUppercase() ? word.getText().toUpperCase() : word.getText();
    }

    // =========================================================================
    // Helpers de color
    // =========================================================================

    private String extractBgr(String assColor) {
        String hex = assColor.replaceFirst("^&H", "").replaceAll("&$", "");
        while (hex.length() < 8) hex = "0" + hex;
        return hex.substring(2).toUpperCase();
    }

    // =========================================================================
    // Helpers de color
    // =========================================================================

    private String toAssFullColor(String assColor) {
        String hex = assColor.replaceFirst("^&H", "").replaceAll("&$", "");
        while (hex.length() < 8) hex = "0" + hex;
        return "&H" + hex.toUpperCase();
    }

    // =========================================================================
    // Agrupacion y formato
    // =========================================================================

    private List<List<TimedWord>> groupWordsIntoLines(List<TimedWord> words, int perLine) {
        List<List<TimedWord>> lines = new ArrayList<>();
        List<TimedWord> current = new ArrayList<>();

        for (int i = 0; i < words.size(); i++) {
            current.add(words.get(i));
            boolean limit = current.size() >= perLine;
            boolean last  = i == words.size() - 1;
            boolean pause = !last
                    && (words.get(i + 1).getStartMs() - words.get(i).getEndMs() > 500);

            if (limit || pause || last) {
                lines.add(new ArrayList<>(current));
                current.clear();
            }
        }
        return lines;
    }

    public static String formatAssTime(long ms) {
        long h  = ms / 3_600_000;
        long m  = (ms % 3_600_000) / 60_000;
        long s  = (ms % 60_000) / 1_000;
        long cs = (ms % 1_000) / 10;
        return String.format("%d:%02d:%02d.%02d", h, m, s, cs);
    }
}