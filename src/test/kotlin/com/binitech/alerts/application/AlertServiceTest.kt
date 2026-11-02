package com.binitech.alerts.application

import com.binitech.alerts.InMemoryAlerts
import com.binitech.alerts.MutableClock
import com.binitech.alerts.RecordingBroadcast
import com.binitech.alerts.application.usecases.AlertService
import com.binitech.alerts.domain.AlertKind
import com.binitech.alerts.domain.ResourceNotFoundException
import com.binitech.alerts.domain.Severity
import com.binitech.alerts.domain.SlowRequest
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AlertServiceTest {

    private val repo = InMemoryAlerts()
    private val broadcast = RecordingBroadcast()
    private val clock = MutableClock()
    private val service = AlertService(repo, broadcast, clock, 1000, Duration.ofMinutes(5))

    private fun slow(ms: Long, path: String = "/api/sales", status: Int = 201) =
        SlowRequest("pdv", "POST", path, status, ms, clock.instant())

    @Test
    @DisplayName("Requisição abaixo de 1 s não gera alerta")
    fun ignoresFastRequests() {
        assertNull(service.registerSlowRequest(slow(999)))
        assertTrue(broadcast.events.isEmpty())
    }

    @Test
    @DisplayName("Acima de 1 s gera alerta barulhento; 5x o limite ou 5xx vira crítico")
    fun raisesNoisyAlert() {
        val warning = service.registerSlowRequest(slow(1200))!!
        val critical = service.registerSlowRequest(slow(5200, path = "/api/products"))!!
        val error = service.registerSlowRequest(slow(1100, path = "/api/auth/login", status = 503))!!

        assertEquals(Severity.WARNING, warning.severity)
        assertEquals(AlertKind.SLOW_REQUEST, warning.kind)
        assertTrue(warning.noisy)
        assertEquals("pdv: resposta lenta (1,2 s)", warning.title)
        assertEquals(Severity.CRITICAL, critical.severity)
        assertEquals(Severity.CRITICAL, error.severity)
        assertEquals(3, broadcast.noisyAlerts().size)
    }

    @Test
    @DisplayName("Repetições dentro do cooldown somam ocorrências sem tocar o alarme de novo")
    fun groupsWithinCooldown() {
        val first = service.registerSlowRequest(slow(1200))!!
        clock.advance(Duration.ofMinutes(2))
        val again = service.registerSlowRequest(slow(3000).copy(suppressed = 4))!!

        assertEquals(first.id, again.id)
        assertEquals(6, again.occurrences)
        assertEquals(3000, again.durationMs)
        assertEquals(1, broadcast.noisyAlerts().size)
        assertEquals("update", broadcast.events.last().first)

        clock.advance(Duration.ofMinutes(6))
        val later = service.registerSlowRequest(slow(1300))!!
        assertTrue(later.id != first.id)
        assertEquals(2, broadcast.noisyAlerts().size)
    }

    @Test
    @DisplayName("Reconhecer encerra o agrupamento e o próximo lento toca de novo")
    fun acknowledge() {
        val first = service.registerSlowRequest(slow(1200))!!
        val acked = service.acknowledge(first.id!!)
        assertNotNull(acked.acknowledgedAt)
        assertEquals(acked, service.acknowledge(first.id!!))

        service.registerSlowRequest(slow(1200))
        assertEquals(2, broadcast.noisyAlerts().size)
        assertFailsWith<ResourceNotFoundException> { service.acknowledge("nao-existe") }
    }

    @Test
    @DisplayName("Alerta de teste toca o alarme e lista volta do mais recente")
    fun testAlertAndRecent() {
        service.registerSlowRequest(slow(1200))
        clock.advance(Duration.ofSeconds(5))
        val test = service.sendTest()

        assertEquals(AlertKind.TEST, test.kind)
        assertTrue(test.noisy)
        assertEquals(test.id, service.recent(10).first().id)
        assertEquals(1, service.recent(0).size)
    }
}
