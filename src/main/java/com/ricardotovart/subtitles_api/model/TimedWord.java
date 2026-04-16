package com.ricardotovart.subtitles_api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representa una palabra transcrita con sus timestamps.
 * Compatible con el formato de respuesta de Whisper API (verbose_json).
 */
public class TimedWord {

    private String text;

    @JsonProperty("start")
    private double startSeconds;

    @JsonProperty("end")
    private double endSeconds;

    /** Confianza de la transcripción (0.0 - 1.0). Puede ser null si no está disponible. */
    private Double probability;

    public TimedWord() {}

    public TimedWord(String text, double startSeconds, double endSeconds) {
        this.text = text;
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
    }

    // ─── Helpers de conversión ────────────────────────────────────────────────

    /** Retorna el tiempo de inicio en milisegundos. */
    public long getStartMs() {
        return Math.round(startSeconds * 1000);
    }

    /** Retorna el tiempo de fin en milisegundos. */
    public long getEndMs() {
        return Math.round(endSeconds * 1000);
    }

    /** Duración en milisegundos. */
    public long getDurationMs() {
        return getEndMs() - getStartMs();
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public double getStartSeconds() { return startSeconds; }
    public void setStartSeconds(double startSeconds) { this.startSeconds = startSeconds; }

    public double getEndSeconds() { return endSeconds; }
    public void setEndSeconds(double endSeconds) { this.endSeconds = endSeconds; }

    public Double getProbability() { return probability; }
    public void setProbability(Double probability) { this.probability = probability; }

    @Override
    public String toString() {
        return String.format("TimedWord{text='%s', start=%.2fs, end=%.2fs}", text, startSeconds, endSeconds);
    }
}
