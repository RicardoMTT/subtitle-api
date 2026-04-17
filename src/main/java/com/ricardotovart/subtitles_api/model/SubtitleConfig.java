package com.ricardotovart.subtitles_api.model;


/**
 * Configuración de apariencia y comportamiento de los subtítulos.
 * Todos los campos tienen valores por defecto para uso inmediato.
 */


public class SubtitleConfig {

    // ─── Tipografía ───────────────────────────────────────────────────────────

    /** Fuente de los subtítulos. Arial funciona en la mayoría de sistemas. */
    private String fontName = "The Bold Font";

    /** Tamaño de fuente. 72 es ideal para videos verticales (9:16). Para 16:9 usar 56-64. */
    private int fontSize = 72;

    /** Color del texto principal en formato ASS (&HAABBGGRR). Blanco por defecto. */
    private String primaryColor = "&H00FFFFFF";

    /** Color del outline (contorno). Negro para máxima legibilidad. */
    private String outlineColor = "&H00000000";

    /** Grosor del outline en píxeles. */
    private int outlineWidth = 4;

    /** Sombra (0 = sin sombra, 1-4 = sombra). */
    private int shadow = 2;

    // ─── Animaciones ─────────────────────────────────────────────────────────
    // Nota: actualmente las animaciones están desactivadas (corte limpio).
    // Para reactivarlas, restaurar ad y 	 en AssGeneratorService.

    /** Duración del fade-in en ms. 0 = sin fade (corte limpio). */
    private int fadeInMs = 0;

    /** Duración del fade-out en ms. 0 = sin fade (corte limpio). */
    private int fadeOutMs = 0;

    /** Escala máxima del efecto "pop". 100 = sin pop. */
    private int popScalePercent = 100;

    /** Duración de la animación de escala en ms. */
    private int popDurationMs = 0;

    // ─── Palabras destacadas ──────────────────────────────────────────────────

    /** Activar resaltado de la primera palabra de cada línea. */
    private boolean highlightFirstWord = true;

    /** Color de la palabra destacada en formato ASS. Cyan por defecto (como en la imagen). */
    private String highlightColor = "&H00FFFFFF"; // Lila/púrpura: #B44FE8 en BGR

    // ─── Layout ───────────────────────────────────────────────────────────────

    /** Cantidad máxima de palabras por línea de subtítulo. */
    private int wordsPerLine = 4;

    /** Posición vertical (2 = abajo centrado, 8 = arriba centrado). */
    private int alignment = 2;

    /** Margen inferior en píxeles. */
    private int marginBottom = 100;

    /** Margen lateral en píxeles. */
    private int marginSide = 50;

    /** Resolución de referencia para el video (ancho). */
    private int playResX = 1080;

    /** Resolución de referencia para el video (alto). */
    private int playResY = 1920;

    // ─── Mayúsculas ───────────────────────────────────────────────────────────

    /** Convertir texto a mayúsculas (estilo redes sociales). */
    private boolean uppercase = true;

    // ─── Modo de aparición ────────────────────────────────────────────────────

    /**
     * Modo de aparición del texto.
     *
     *  "line"      → toda la línea aparece junta (comportamiento actual con \K karaoke)
     *  "word"      → cada palabra aparece de golpe al pronunciarse, acumulándose
     *                Es el efecto viral de TikTok/Reels: las palabras "pop" una a una.
     */
    private String appearMode = "line";  // "line" | "word"

    // ─── Fondo del texto ─────────────────────────────────────────────────────

    /**
     * Activa el fondo de color detrás del texto (estilo TikTok/CapCut).
     *
     * En ASS se logra con BorderStyle: 3 (opaque box) + BackColour.
     * El fondo es un rectángulo sólido que envuelve cada línea de texto.
     *
     * Modos disponibles (backgroundMode):
     *   "none"   → sin fondo (solo outline/sombra)
     *   "box"    → rectángulo sólido detrás de toda la línea  ← estilo TikTok
     *   "blur"   → semi-transparente (alpha en el color)
     */
    private String backgroundMode = "box";   // "none" | "box" | "blur"

    /**
     * Color del fondo en formato ASS (&HAABBGGRR).
     * AA = alpha (00=opaco, FF=transparente).
     *
     * Ejemplos populares:
     *   Negro opaco:        &H00000000
     *   Negro semi-transp:  &H80000000  (50% alpha)
     *   Blanco opaco:       &H00FFFFFF
     *   Amarillo:           &H0000FFFF
     *   Lila (igual texto): &H00E84FB4
     */
    private String backgroundColor = "&H00000000";  // Negro opaco

    // ─── Builder pattern ─────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private SubtitleConfig config = new SubtitleConfig();

        public Builder fontSize(int size) { config.fontSize = size; return this; }
        public Builder wordsPerLine(int words) { config.wordsPerLine = words; return this; }
        public Builder highlightFirstWord(boolean v) { config.highlightFirstWord = v; return this; }
        public Builder highlightColor(String color) { config.highlightColor = color; return this; }
        public Builder uppercase(boolean v) { config.uppercase = v; return this; }
        public Builder fadeInMs(int ms) { config.fadeInMs = ms; return this; }
        public Builder playRes(int w, int h) { config.playResX = w; config.playResY = h; return this; }
        public Builder outlineWidth(int w) { config.outlineWidth = w; return this; }
        public Builder marginBottom(int m) { config.marginBottom = m; return this; }

        public Builder appearMode(String mode) { config.appearMode = mode; return this; }
        public Builder backgroundMode(String mode) { config.backgroundMode = mode; return this; }
        public Builder backgroundColor(String color) { config.backgroundColor = color; return this; }

        public SubtitleConfig build() { return config; }
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getFontName() { return fontName; }
    public void setFontName(String fontName) { this.fontName = fontName; }

    public int getFontSize() { return fontSize; }
    public void setFontSize(int fontSize) { this.fontSize = fontSize; }

    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

    public String getOutlineColor() { return outlineColor; }
    public void setOutlineColor(String outlineColor) { this.outlineColor = outlineColor; }

    public int getOutlineWidth() { return outlineWidth; }
    public void setOutlineWidth(int outlineWidth) { this.outlineWidth = outlineWidth; }

    public int getShadow() { return shadow; }
    public void setShadow(int shadow) { this.shadow = shadow; }

    public int getFadeInMs() { return fadeInMs; }
    public void setFadeInMs(int fadeInMs) { this.fadeInMs = fadeInMs; }

    public int getFadeOutMs() { return fadeOutMs; }
    public void setFadeOutMs(int fadeOutMs) { this.fadeOutMs = fadeOutMs; }

    public int getPopScalePercent() { return popScalePercent; }
    public void setPopScalePercent(int popScalePercent) { this.popScalePercent = popScalePercent; }

    public int getPopDurationMs() { return popDurationMs; }
    public void setPopDurationMs(int popDurationMs) { this.popDurationMs = popDurationMs; }

    public boolean isHighlightFirstWord() { return highlightFirstWord; }
    public void setHighlightFirstWord(boolean highlightFirstWord) { this.highlightFirstWord = highlightFirstWord; }

    public String getHighlightColor() { return highlightColor; }
    public void setHighlightColor(String highlightColor) { this.highlightColor = highlightColor; }

    public int getWordsPerLine() { return wordsPerLine; }
    public void setWordsPerLine(int wordsPerLine) { this.wordsPerLine = wordsPerLine; }

    public int getAlignment() { return alignment; }
    public void setAlignment(int alignment) { this.alignment = alignment; }

    public int getMarginBottom() { return marginBottom; }
    public void setMarginBottom(int marginBottom) { this.marginBottom = marginBottom; }

    public int getMarginSide() { return marginSide; }
    public void setMarginSide(int marginSide) { this.marginSide = marginSide; }

    public int getPlayResX() { return playResX; }
    public void setPlayResX(int playResX) { this.playResX = playResX; }

    public int getPlayResY() { return playResY; }
    public void setPlayResY(int playResY) { this.playResY = playResY; }

    public boolean isUppercase() { return uppercase; }
    public void setUppercase(boolean uppercase) { this.uppercase = uppercase; }

    public String getAppearMode() { return appearMode; }
    public void setAppearMode(String appearMode) { this.appearMode = appearMode; }

    public boolean isWordByWord() { return "word".equalsIgnoreCase(appearMode); }

    public String getBackgroundMode() { return backgroundMode; }
    public void setBackgroundMode(String backgroundMode) { this.backgroundMode = backgroundMode; }

    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }

    /** Retorna true si el modo de fondo está activo (box o blur). */
    public boolean hasBackground() { return !"none".equalsIgnoreCase(backgroundMode); }
}
