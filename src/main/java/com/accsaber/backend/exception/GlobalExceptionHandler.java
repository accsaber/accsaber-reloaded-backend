package com.accsaber.backend.exception;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.accsaber.backend.model.dto.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(AccSaberException.class)
        public ResponseEntity<ErrorResponse> handleAccSaberException(
                        AccSaberException ex, HttpServletRequest request) {
                log.warn("AccSaber exception: {} - {}", ex.getErrorCode(), ex.getMessage());

                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(Instant.now())
                                .status(ex.getStatus().value())
                                .error(ex.getStatus().getReasonPhrase())
                                .code(ex.getErrorCode())
                                .message(ex.getMessage())
                                .path(request.getRequestURI())
                                .correlationId(MDC.get("correlationId"))
                                .build();

                return ResponseEntity.status(ex.getStatus()).body(response);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidationException(
                        MethodArgumentNotValidException ex, HttpServletRequest request) {
                log.warn("Validation exception: {}", ex.getMessage());

                List<ErrorResponse.FieldError> fieldErrors = extractFieldErrors(ex.getBindingResult());

                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(Instant.now())
                                .status(HttpStatus.UNPROCESSABLE_CONTENT.value())
                                .error(HttpStatus.UNPROCESSABLE_CONTENT.getReasonPhrase())
                                .code("VALIDATION_ERROR")
                                .message("Validation failed")
                                .path(request.getRequestURI())
                                .correlationId(MDC.get("correlationId"))
                                .fieldErrors(fieldErrors)
                                .build();

                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(response);
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<ErrorResponse> handleTypeMismatch(
                        MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
                log.warn("Type mismatch: {}", ex.getMessage());

                String message = String.format("Parameter '%s' must be of type %s",
                                ex.getName(),
                                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(Instant.now())
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                                .code("TYPE_MISMATCH")
                                .message(message)
                                .path(request.getRequestURI())
                                .correlationId(MDC.get("correlationId"))
                                .build();

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAccessDenied(
                        AccessDeniedException ex, HttpServletRequest request) {
                log.warn("Access denied: {}", ex.getMessage());

                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(Instant.now())
                                .status(HttpStatus.FORBIDDEN.value())
                                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                                .code("FORBIDDEN")
                                .message("You do not have permission to access this resource")
                                .path(request.getRequestURI())
                                .correlationId(MDC.get("correlationId"))
                                .build();

                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ErrorResponse> handleAuthenticationException(
                        AuthenticationException ex, HttpServletRequest request) {
                log.warn("Authentication failed: {}", ex.getMessage());

                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(Instant.now())
                                .status(HttpStatus.UNAUTHORIZED.value())
                                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                                .code("UNAUTHORIZED")
                                .message("Authentication required")
                                .path(request.getRequestURI())
                                .correlationId(MDC.get("correlationId"))
                                .build();

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ErrorResponse> handleNoResourceFound(
                        NoResourceFoundException ex, HttpServletRequest request) {
                log.warn("Resource not found: {}", request.getRequestURI());

                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(Instant.now())
                                .status(HttpStatus.NOT_FOUND.value())
                                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                                .code("NOT_FOUND")
                                .message("The requested resource was not found")
                                .path(request.getRequestURI())
                                .correlationId(MDC.get("correlationId"))
                                .build();

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        @ExceptionHandler(InvalidDataAccessApiUsageException.class)
        public ResponseEntity<ErrorResponse> handleInvalidDataAccessApiUsage(
                        InvalidDataAccessApiUsageException ex, HttpServletRequest request) {
                log.warn("Invalid data access usage: {}", ex.getMessage());

                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(Instant.now())
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                                .code("INVALID_PARAMETER")
                                .message(ex.getMostSpecificCause().getMessage())
                                .path(request.getRequestURI())
                                .correlationId(MDC.get("correlationId"))
                                .build();

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGenericException(
                        Exception ex, HttpServletRequest request) {
                log.error("Unexpected error", ex);

                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(Instant.now())
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                                .code("INTERNAL_ERROR")
                                .message("An unexpected error occurred")
                                .path(request.getRequestURI())
                                .correlationId(MDC.get("correlationId"))
                                .build();

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        private List<ErrorResponse.FieldError> extractFieldErrors(BindingResult bindingResult) {
                return bindingResult.getFieldErrors().stream()
                                .map(error -> ErrorResponse.FieldError.builder()
                                                .field(error.getField())
                                                .message(error.getDefaultMessage())
                                                .rejectedValue(error.getRejectedValue())
                                                .build())
                                .toList();
        }
}
