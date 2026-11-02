package com.binitech.alerts.adapters

import com.binitech.alerts.adapters.inbound.web.AlertsController
import com.binitech.alerts.adapters.inbound.web.ApiTokenFilter
import com.binitech.alerts.adapters.inbound.web.GlobalExceptionHandler
import com.binitech.alerts.adapters.outbound.push.SseAlertBroadcaster
import com.binitech.alerts.application.ports.inbound.AlertUseCase
import com.binitech.alerts.application.ports.inbound.ProbeUseCase
import com.binitech.alerts.domain.Alert
import com.binitech.alerts.domain.AlertKind
import com.binitech.alerts.domain.ResourceNotFoundException
import com.binitech.alerts.domain.Severity
import com.binitech.alerts.domain.TargetStatus
import java.time.Instant
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(
    controllers = [AlertsController::class],
    properties = ["alerts.api-token=token-de-teste-com-mais-de-32-caracteres"],
)
@Import(GlobalExceptionHandler::class, AlertsApiContractTest.TokenFilter::class)
class AlertsApiContractTest {

    @TestConfiguration
    class TokenFilter {
        @Bean
        fun apiTokenFilter() = FilterRegistrationBean(ApiTokenFilter(TOKEN)).apply { addUrlPatterns("/api/*") }
    }

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var alerts: AlertUseCase

    @MockitoBean
    private lateinit var probes: ProbeUseCase

    @MockitoBean
    private lateinit var stream: SseAlertBroadcaster

    private val now = Instant.parse("2026-11-01T12:00:00Z")

    private fun alert(acknowledgedAt: Instant? = null) = Alert(
        id = "a1",
        kind = AlertKind.SLOW_REQUEST,
        severity = Severity.WARNING,
        service = "pdv",
        title = "pdv: resposta lenta (1,4 s)",
        message = "POST /api/sales respondeu 201 em 1400 ms (limite 1000 ms).",
        fingerprint = "slow:pdv:POST /api/sales",
        method = "POST",
        path = "/api/sales",
        status = 201,
        durationMs = 1400,
        thresholdMs = 1000,
        occurrences = 3,
        firstSeenAt = now,
        lastSeenAt = now,
        acknowledgedAt = acknowledgedAt,
    )

    @Test
    @DisplayName("Sem o token correto a API responde 401 e o health é público")
    fun requiresToken() {
        mvc.perform(get("/api/alerts")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/alerts").header("Authorization", "Bearer errado"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Token inválido."))
    }

    @Test
    @DisplayName("Lista alertas no contrato AlertDTO")
    fun listsAlerts() {
        `when`(alerts.recent(20)).thenReturn(listOf(alert()))
        mvc.perform(get("/api/alerts?limit=20").header("Authorization", "Bearer $TOKEN"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("a1"))
            .andExpect(jsonPath("$[0].kind").value("SLOW_REQUEST"))
            .andExpect(jsonPath("$[0].severity").value("WARNING"))
            .andExpect(jsonPath("$[0].durationMs").value(1400))
            .andExpect(jsonPath("$[0].occurrences").value(3))
            .andExpect(jsonPath("$[0].noisy").value(true))
            .andExpect(jsonPath("$[0].acknowledged").value(false))
    }

    @Test
    @DisplayName("Reconhece alerta, 404 se não existir, e dispara teste com 201")
    fun acknowledgeAndTest() {
        `when`(alerts.acknowledge("a1")).thenReturn(alert(now))
        `when`(alerts.acknowledge("x")).thenThrow(ResourceNotFoundException("Alerta não encontrado."))
        `when`(alerts.sendTest()).thenReturn(alert().copy(kind = AlertKind.TEST))

        mvc.perform(post("/api/alerts/a1/ack").header("Authorization", "Bearer $TOKEN"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledged").value(true))
        mvc.perform(post("/api/alerts/x/ack").header("Authorization", "Bearer $TOKEN"))
            .andExpect(status().isNotFound)
        mvc.perform(post("/api/alerts/test").header("Authorization", "Bearer $TOKEN"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.kind").value("TEST"))
    }

    @Test
    @DisplayName("Mostra a situação das sondas")
    fun listsTargets() {
        `when`(probes.statuses()).thenReturn(
            listOf(TargetStatus("pdv", "https://pdv/actuator/health", false, 200, 2300, null, now)),
        )
        mvc.perform(get("/api/alerts/targets").header("Authorization", "Bearer $TOKEN"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("pdv"))
            .andExpect(jsonPath("$[0].healthy").value(false))
            .andExpect(jsonPath("$[0].lastDurationMs").value(2300))
    }

    companion object {
        private const val TOKEN = "token-de-teste-com-mais-de-32-caracteres"
    }
}
