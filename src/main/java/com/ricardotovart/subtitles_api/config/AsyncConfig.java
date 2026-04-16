package com.ricardotovart.subtitles_api.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuración del pool de hilos para procesamiento asíncrono.
 *
 * El procesamiento de video puede tardar varios minutos.
 * @Async evita bloquear el hilo HTTP principal (Tomcat).
 *
 * Sizing recomendado:
 *   - corePoolSize: cantidad de videos procesándose simultáneamente
 *   - maxPoolSize:  pico máximo (usa más CPU/RAM temporalmente)
 *   - queueCapacity: trabajos esperando en cola
 *
 * Ajusta según tu hardware. Con 8 cores:
 *   - corePoolSize: 2-3 (FFmpeg es CPU-intensivo)
 *   - maxPoolSize:  4
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${async.core-pool-size:2}")
    private int corePoolSize;

    @Value("${async.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${async.queue-capacity:10}")
    private int queueCapacity;

    @Value("${async.thread-name-prefix:subtitle-async-}")
    private String threadNamePrefix;

    @Bean(name = "subtitleTaskExecutor")
    public Executor subtitleTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }
}