package com.binitech.alerts.application

import com.binitech.alerts.InMemoryAlerts
import com.binitech.alerts.MutableClock
import com.binitech.alerts.RecordingBroadcast
import com.binitech.alerts.ScriptedProbe
import com.binitech.alerts.application.usecases.AlertService
import com.binitech.alerts.application.usecases.ProbeService
import com.binitech.alerts.config.AlertsProperties
import com.binitech.alerts.domain.AlertKind
import com.binitech.alerts.domain.ProbeTarget
import com.binitech.alerts.domain.Severity
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProbeServiceTest {

    private val broadcast = RecordingBroadcast()
    private val clock = MutableClock()
    private val alerts = AlertService(InMemoryAlerts(), broadcast, clock, 1000, Duration.ofMinutes(5))
    private val probe = ScriptedProbe()
    private val target = ProbeTarget("pdv", "https://pdv/actuator/health")
    private val service = ProbeService(probe, alerts, listOf(target), clock, 1000, 2)

    private fun kinds() = broadcast.events.filter { it.first == "alert" }.map { it.second.kind }

    @Test
    @DisplayName("Um único resultado ruim não alerta; dois seguidos alertam")
    fun needsConfirmation() {
        probe.slow()
        service.probeAll()
        assertTrue(kinds().isEmpty())
        assertFalse(service.statuses().single().healthy)

        probe.slow(2300)
        service.probeAll()
        assertEquals(listOf(AlertKind.PROBE_SLOW), kinds())
        assertEquals("pdv: lento (2,3 s)", broadcast.events.last().second.title)
    }

    @Test
    @DisplayName("Fora do ar é crítico e a volta gera aviso silencioso de normalização")
    fun downAndRecovered() {
        probe.down()
        probe.down()
        service.probeAll()
        service.probeAll()
        val down = broadcast.events.last().second
        assertEquals(AlertKind.PROBE_DOWN, down.kind)
        assertEquals(Severity.CRITICAL, down.severity)

        probe.ok()
        service.probeAll()
        val recovered = broadcast.events.last().second
        assertEquals(AlertKind.PROBE_RECOVERED, recovered.kind)
        assertFalse(recovered.noisy)
        assertTrue(service.statuses().single().healthy)
    }

    @Test
    @DisplayName("Lento intercalado com normal não alerta")
    fun flappingDoesNotAlert() {
        probe.slow()
        probe.ok()
        probe.slow()
        probe.ok()
        repeat(4) { service.probeAll() }
        assertTrue(kinds().isEmpty())
    }

    @Test
    @DisplayName("Lista de alvos vem de nome=url separados por vírgula")
    fun parsesTargets() {
        val properties = AlertsProperties(
            apiToken = "x".repeat(32),
            probeTargets = " pdv=https://a/actuator/health , site=https://b ",
        )
        assertEquals(
            listOf(ProbeTarget("pdv", "https://a/actuator/health"), ProbeTarget("site", "https://b")),
            properties.targets(),
        )
        assertThrows<IllegalArgumentException> { AlertsProperties(apiToken = "x", probeTargets = "sem-url").targets() }
    }
}
