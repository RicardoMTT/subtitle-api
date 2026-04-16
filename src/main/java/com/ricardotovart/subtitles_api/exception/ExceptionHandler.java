package com.ricardotovart.subtitles_api.exception;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class ExceptionHandler {

    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAllExceptions(Exception ex) {
        System.out.println("Error no controlado: " + ex.getMessage());
        return ResponseEntity.internalServerError().body(ex.getMessage());
    }
}
