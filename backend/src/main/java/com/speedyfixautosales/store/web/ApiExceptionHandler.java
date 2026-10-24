package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.common.BadRequestException;
import com.speedyfixautosales.store.common.ConflictException;
import com.speedyfixautosales.store.common.NotFoundException;
import com.speedyfixautosales.store.common.TooManyRequestsException;
import com.speedyfixautosales.store.common.UnauthorizedException;
import com.speedyfixautosales.store.common.UnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Returns RFC 7807 problem details. "detail" is written for shoppers and shown as-is by the frontend. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail badRequest(BadRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, "Check your details", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Not enough stock", e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail unauthorized(UnauthorizedException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Sign in", e.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ProblemDetail tooMany(TooManyRequestsException e) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts", e.getMessage());
    }

    @ExceptionHandler(UnavailableException.class)
    public ProblemDetail unavailable(UnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Not set up", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.isBindingFailure() ? "Check the " + err.getField() + " and try again." : err.getDefaultMessage())
                .orElse("Check the details and try again.");
        return problem(HttpStatus.BAD_REQUEST, "Check your details", detail);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, "Check your details", "Check the " + e.getName() + " and try again.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "Check your details", "Check the details and try again.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Photos too large", "Each photo must be under 5 MB, and 16 MB in total.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
