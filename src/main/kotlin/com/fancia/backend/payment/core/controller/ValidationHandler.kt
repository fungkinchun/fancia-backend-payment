package com.fancia.backend.payment.core.controller

import com.fancia.backend.payment.config.ApplicationProperties
import com.fancia.backend.shared.common.core.exception.DomainException
import com.fasterxml.jackson.databind.JsonMappingException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import java.net.URI

@RestControllerAdvice
class ValidationHandler(
    private val applicationProperties: ApplicationProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: WebRequest): ProblemDetail {
        val errors = ex.bindingResult.allErrors.map { it.defaultMessage ?: "error" }
        val errorCode = "VALIDATION_ERROR"
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed: ${errors.joinToString(", ")}",
        ).apply {
            applicationProperties.baseUrl?.let { type = URI.create(it) }
            title = "Validation Error"
            setProperty("errorCode", errorCode)
            instance = URI.create((request as ServletWebRequest).request.requestURI)
        }
        log.warn("Validation error: ${errors.joinToString(", ")}")
        return problem
    }

    @ExceptionHandler(DomainException::class)
    fun handleError(ex: DomainException, request: WebRequest): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.message,
        ).apply {
            applicationProperties.baseUrl?.let {
                type = URI.create(it)
            }
            title = ex.title
            setProperty("errorCode", ex.errorCode)
            instance = URI.create((request as ServletWebRequest).request.requestURI)
        }
        log.warn("Domain error: ${ex.errorCode} - ${ex.message}")
        return problem
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException): ProblemDetail {
        val path = (ex.mostSpecificCause as? JsonMappingException)
            ?.path
            ?.joinToString(".") { it.fieldName ?: "[${it.index}]" }
            ?: "unknown"
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            path,
        ).apply {
            applicationProperties.baseUrl?.let { type = URI.create(it) }
            title = "Invalid Request Body"
            setProperty("errorCode", "INVALID_REQUEST_BODY")
        }
        return problem
    }
}
