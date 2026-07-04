package com.streamhelper.app.web;

import com.streamhelper.app.ai.AiClientException;
import com.streamhelper.app.project.NotFoundException;
import com.streamhelper.app.project.StorageException;
import com.streamhelper.app.transcription.TranscriptionException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "NOT_FOUND", "message", exception.getMessage()));
    }

    @ExceptionHandler({
        StorageException.class, AiClientException.class, TranscriptionException.class
    })
    public ResponseEntity<Map<String, Object>> badRequest(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "REQUEST_FAILED", "message", exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<Map<String, Object>> validation(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "VALIDATION_ERROR", "message", exception.getMessage()));
    }
}
