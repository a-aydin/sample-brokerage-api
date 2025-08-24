package com.fintech.brokerage.exception.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import com.fintech.brokerage.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	// Enhanced DTO for standardized error response
	public record ErrorResponse(String error, Object message, String path, LocalDateTime timestamp, int status) {
		public static ErrorResponse of(String error, Object message, String path, HttpStatus status) {
			return new ErrorResponse(error, message, path, LocalDateTime.now(), status.value());
		}
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {

		List<String> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(err -> err.getField() + ": " + err.getDefaultMessage()).toList(); // Java 16+ replacement for
																						// collect(Collectors.toList())

		log.warn("Validation failed for request {}: {}", request.getDescription(false), errors);

		ErrorResponse errorResponse = ErrorResponse.of("validation_error", errors, request.getDescription(false),
				HttpStatus.BAD_REQUEST);

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
	}

	@ExceptionHandler({ IllegalStateException.class, IllegalArgumentException.class })
	public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex, WebRequest request) {

		log.warn("Bad request for {}: {}", request.getDescription(false), ex.getMessage(), ex);

		ErrorResponse errorResponse = ErrorResponse.of("bad_request", ex.getMessage(), request.getDescription(false),
				HttpStatus.BAD_REQUEST);

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, WebRequest request) {

		log.warn("Access denied for {}: {}", request.getDescription(false), ex.getMessage());

		ErrorResponse errorResponse = ErrorResponse.of("access_denied", "Access denied", request.getDescription(false),
				HttpStatus.FORBIDDEN);

		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
	}

	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex, WebRequest request) {

		log.info("Resource not found for {}: {}", request.getDescription(false), ex.getMessage());

		ErrorResponse errorResponse = ErrorResponse.of("not_found", "Resource not found", request.getDescription(false),
				HttpStatus.NOT_FOUND);

		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
	}

	// Handle business logic exceptions (if you have custom ones)
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, WebRequest request) {

		log.warn("Business logic error for {}: {}", request.getDescription(false), ex.getMessage());

		ErrorResponse errorResponse = ErrorResponse.of("business_error", ex.getMessage(), request.getDescription(false),
				HttpStatus.BAD_REQUEST);

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
	}

	// Catch-all for other exceptions
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, WebRequest request) {
		log.error("Unhandled exception for {}", request.getDescription(false), ex);

		ErrorResponse errorResponse = ErrorResponse.of("internal_error", "An unexpected error occurred",
				request.getDescription(false), HttpStatus.INTERNAL_SERVER_ERROR);

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
	}
}