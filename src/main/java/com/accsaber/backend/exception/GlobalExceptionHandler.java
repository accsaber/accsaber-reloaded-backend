package com.accsaber.backend.exception;

import java.time.Instant;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.accsaber.backend.model.dto.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(AccSaberException.class)
        public ResponseEntity<ErrorResponse> handleAccSaberException(
                        AccSaberException ex, HttpServletRequest request) {
                log.warn("AccSaber exception: {} - {}", ex.getErrorCode(), ex.getMessage());
                return respond(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidationException(
                        MethodArgumentNotValidException ex, HttpServletRequest request) {
                log.warn("Validation exception: {}", ex.getMessage());

                ErrorResponse response = body(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR",
                                "Validation failed", request)
                                .fieldErrors(extractFieldErrors(ex.getBindingResult()))
                                .build();

                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(response);
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ErrorResponse> handleUnreadableBody(
                        HttpMessageNotReadableException ex, HttpServletRequest request) {
                log.warn("Malformed request body on {}: {}", request.getRequestURI(), ex.getMessage());
                return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY",
                                "Request body is missing or could not be parsed", request);
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<ErrorResponse> handleTypeMismatch(
                        MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
                log.warn("Type mismatch: {}", ex.getMessage());

                String message = String.format("Parameter '%s' must be of type %s",
                                ex.getName(),
                                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

                return respond(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", message, request);
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAccessDenied(
                        AccessDeniedException ex, HttpServletRequest request) {
                log.warn("Access denied: {}", ex.getMessage());
                return respond(HttpStatus.FORBIDDEN, "FORBIDDEN",
                                "You do not have permission to access this resource", request);
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ErrorResponse> handleAuthenticationException(
                        AuthenticationException ex, HttpServletRequest request) {
                log.warn("Authentication failed: {}", ex.getMessage());
                return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required", request);
        }

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ErrorResponse> handleNoResourceFound(
                        NoResourceFoundException ex, HttpServletRequest request) {
                log.warn("Resource not found: {}", request.getRequestURI());
                return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found", request);
        }

        @ExceptionHandler(PropertyReferenceException.class)
        public ResponseEntity<ErrorResponse> handlePropertyReferenceException(
                        PropertyReferenceException ex, HttpServletRequest request) {
                log.warn("Invalid property reference: {}", ex.getMessage());

                String message = String.format("Invalid sort property '%s'", ex.getPropertyName());

                return respond(HttpStatus.BAD_REQUEST, "INVALID_PROPERTY", message, request);
        }

        @ExceptionHandler(InvalidDataAccessApiUsageException.class)
        public ResponseEntity<ErrorResponse> handleInvalidDataAccessApiUsage(
                        InvalidDataAccessApiUsageException ex, HttpServletRequest request) {
                log.warn("Invalid data access usage on {}: {}", request.getRequestURI(),
                                ex.getMostSpecificCause().getMessage());
                return respond(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid request parameter", request);
        }

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ErrorResponse> handleMaxUploadSize(
                        MaxUploadSizeExceededException ex, HttpServletRequest request) {
                log.warn("Upload exceeded max size on {}: {}", request.getRequestURI(), ex.getMessage());
                return respond(HttpStatus.CONTENT_TOO_LARGE, "UPLOAD_TOO_LARGE",
                                "Uploaded file exceeds the maximum allowed size", request);
        }

        @ExceptionHandler(MissingServletRequestPartException.class)
        public ResponseEntity<ErrorResponse> handleMissingPart(
                        MissingServletRequestPartException ex, HttpServletRequest request) {
                log.warn("Missing multipart part '{}' on {}", ex.getRequestPartName(), request.getRequestURI());
                return respond(HttpStatus.BAD_REQUEST, "MISSING_MULTIPART_PART",
                                String.format("Required multipart part '%s' is missing", ex.getRequestPartName()),
                                request);
        }

        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<ErrorResponse> handleMissingParameter(
                        MissingServletRequestParameterException ex, HttpServletRequest request) {
                log.warn("Missing required parameter '{}' on {}", ex.getParameterName(), request.getRequestURI());
                return respond(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                                String.format("Required parameter '%s' is missing", ex.getParameterName()), request);
        }

        @ExceptionHandler(MultipartException.class)
        public ResponseEntity<ErrorResponse> handleMultipart(
                        MultipartException ex, HttpServletRequest request) {
                log.warn("Malformed multipart request on {}: {}", request.getRequestURI(), ex.getMessage());
                return respond(HttpStatus.BAD_REQUEST, "MALFORMED_MULTIPART", "Multipart request could not be parsed",
                                request);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGenericException(
                        Exception ex, HttpServletRequest request) {
                log.error("Unexpected error", ex);
                return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred",
                                request);
        }

        private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message,
                        HttpServletRequest request) {
                return ResponseEntity.status(status).body(body(status, code, message, request).build());
        }

        private static ErrorResponse.ErrorResponseBuilder body(HttpStatus status, String code, String message,
                        HttpServletRequest request) {
                return ErrorResponse.builder()
                                .timestamp(Instant.now())
                                .status(status.value())
                                .error(status.getReasonPhrase())
                                .code(code)
                                .message(message)
                                .path(request.getRequestURI())
                                .correlationId(MDC.get("correlationId"));
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
