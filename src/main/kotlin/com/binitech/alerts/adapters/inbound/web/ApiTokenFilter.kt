package com.binitech.alerts.adapters.inbound.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter

class ApiTokenFilter(apiToken: String) : OncePerRequestFilter() {

    private val expected = apiToken.toByteArray(StandardCharsets.UTF_8)

    init {
        require(apiToken.length >= MIN_TOKEN_LENGTH) { "ALERTS_API_TOKEN deve ter ao menos $MIN_TOKEN_LENGTH caracteres." }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith("/api/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val header = request.getHeader("Authorization").orEmpty()
        val supplied = header.removePrefix("Bearer ").toByteArray(StandardCharsets.UTF_8)
        if (!header.startsWith("Bearer ") || !MessageDigest.isEqual(expected, supplied)) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = StandardCharsets.UTF_8.name()
            response.writer.write("{\"message\":\"Token inválido.\"}")
            return
        }
        chain.doFilter(request, response)
    }

    companion object {
        private const val MIN_TOKEN_LENGTH = 32
    }
}
