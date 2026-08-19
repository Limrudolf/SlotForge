package com.slotforge.api.common.error;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import com.slotforge.api.auth.EmailAlreadyRegisteredException;
import com.slotforge.api.auth.InvalidCredentialsException;
import com.slotforge.api.event.EventNotFoundException;
import com.slotforge.api.event.EventOwnershipException;
import com.slotforge.api.refreshtoken.InvalidRefreshTokenException;
import com.slotforge.api.session.EventSessionNotFoundException;
import com.slotforge.api.user.AuthenticatedAccountUnavailableException;
import com.slotforge.api.venue.VenueNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            EventNotFoundException.class,
            VenueNotFoundException.class,
            EventSessionNotFoundException.class
    })
    public ResponseEntity<ApiError> handleResourceNotFound(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleRequestBodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiFieldError> fieldErrors = new ArrayList<>();

        for (FieldError error
                : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new ApiFieldError(
                    error.getField(),
                    error.getDefaultMessage()
            ));
        }

        for (ObjectError error
                : exception.getBindingResult().getGlobalErrors()) {
            fieldErrors.add(new ApiFieldError(
                    "request",
                    error.getDefaultMessage()
            ));
        }

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request.getRequestURI(),
                fieldErrors
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        List<ApiFieldError> fieldErrors = new ArrayList<>();

        for (ParameterValidationResult result
                : exception.getParameterValidationResults()) {

            String parameterName = result
                    .getMethodParameter()
                    .getParameterName();

            String field = parameterName == null
                    ? "parameter"
                    : parameterName;

            result.getResolvableErrors().forEach(error ->
                    fieldErrors.add(new ApiFieldError(
                            field,
                            error.getDefaultMessage()
                    ))
            );
        }

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request.getRequestURI(),
                fieldErrors
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        ApiFieldError fieldError = new ApiFieldError(
                exception.getName(),
                "Invalid value: " + exception.getValue()
        );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Request parameter has an invalid type or value",
                request.getRequestURI(),
                List.of(fieldError)
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Request body is malformed or contains an invalid value",
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "The resource was modified by another request",
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage(),
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(AuthenticatedAccountUnavailableException.class)
    public ResponseEntity<ApiError> handleUnavailableAuthenticatedAccount(
            AuthenticatedAccountUnavailableException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage(),
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(
            InvalidRefreshTokenException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage(),
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(EventOwnershipException.class)
    public ResponseEntity<ApiError> handleEventOwnership(
            EventOwnershipException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.FORBIDDEN,
                exception.getMessage(),
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "The request conflicts with existing data",
                request.getRequestURI(),
                List.of()
        );
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String message,
            String path,
            List<ApiFieldError> fieldErrors
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                fieldErrors
        );

        return ResponseEntity
                .status(status)
                .body(error);
    }
}
