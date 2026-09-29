package com.domainprobe.api.exception;

import com.domainprobe.api.dto.common.ErrorResponseDTO;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        List<ErrorResponseDTO.FieldValidationError> validationErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toValidationError)
                .toList();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "Request contains invalid fields",
                validationErrors
        );
    }

    @ExceptionHandler({
            BadRequestException.class,
            IllegalArgumentException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponseDTO> handleBadRequestException(Exception exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad request", exception.getMessage());
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponseDTO> handleExternalApiException(ExternalApiException exception) {
        return buildResponse(
                HttpStatus.BAD_GATEWAY,
                "RDAP unavailable",
                exception.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception exception) {
        log.error("Unhandled exception: {}", exception.getClass().getName(), exception);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "Something went wrong"
        );
    }

    private ErrorResponseDTO.FieldValidationError toValidationError(FieldError fieldError) {
        return ErrorResponseDTO.FieldValidationError.builder()
                .field(fieldError.getField())
                .message(fieldError.getDefaultMessage())
                .build();
    }

    private ResponseEntity<ErrorResponseDTO> buildResponse(
            HttpStatus status,
            String error,
            String message
    ) {
        return buildResponse(status, error, message, null);
    }

    private ResponseEntity<ErrorResponseDTO> buildResponse(
            HttpStatus status,
            String error,
            String message,
            List<ErrorResponseDTO.FieldValidationError> validationErrors
    ) {
        ErrorResponseDTO body = ErrorResponseDTO.builder()
                .error(error)
                .message(message)
                .status(status.value())
                .validationErrors(validationErrors)
                .build();

        return new ResponseEntity<>(body, status);
    }
}
