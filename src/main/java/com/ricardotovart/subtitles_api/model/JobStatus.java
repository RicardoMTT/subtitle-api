package com.ricardotovart.subtitles_api.model;

import java.nio.file.Path;

public record JobStatus(String jobId, String status, Path outputPath, String errorMessage) {

    public static JobStatus processing(String jobId) {
        return new JobStatus(jobId, "PROCESSING", null, null);
    }
    public static JobStatus completed(String jobId, Path path) {
        return new JobStatus(jobId, "COMPLETED", path, null);
    }
    public static JobStatus failed(String jobId, String error) {
        return new JobStatus(jobId, "FAILED", null, error);
    }

}
