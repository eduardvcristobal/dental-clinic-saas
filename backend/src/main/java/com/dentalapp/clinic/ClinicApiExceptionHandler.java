package com.dentalapp.clinic;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Returns safe, actionable validation details for clinic APIs without leaking SQL internals. */
@RestControllerAdvice(basePackageClasses = ClinicOnboardingController.class)
public class ClinicApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail clinicError(ResponseStatusException exception) {
        HttpStatusCode status = exception.getStatusCode();
        String detail = exception.getReason();
        if (detail == null || detail.isBlank()) detail = "The request could not be completed.";
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
