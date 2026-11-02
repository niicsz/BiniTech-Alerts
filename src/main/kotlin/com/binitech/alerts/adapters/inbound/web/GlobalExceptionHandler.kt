package com.binitech.alerts.adapters.inbound.web

import com.binitech.alerts.adapters.inbound.web.generated.model.ErrorDTO
import com.binitech.alerts.domain.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException::class)
    fun notFound(e: ResourceNotFoundException): ResponseEntity<ErrorDTO> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorDTO(message = e.message))

    @ExceptionHandler(
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        HandlerMethodValidationException::class,
        IllegalArgumentException::class,
    )
    fun badRequest(e: Exception): ResponseEntity<ErrorDTO> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorDTO(message = "Requisição inválida."))
}
