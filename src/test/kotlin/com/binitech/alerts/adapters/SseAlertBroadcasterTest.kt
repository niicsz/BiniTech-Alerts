package com.binitech.alerts.adapters

import com.binitech.alerts.adapters.outbound.push.SseAlertBroadcaster
import com.binitech.alerts.domain.Alert
import com.binitech.alerts.domain.AlertKind
import com.binitech.alerts.domain.Severity
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SseAlertBroadcasterTest {

    @Test
    @DisplayName("O payload enviado ao app tem os campos do AlertDTO e indica se faz barulho")
    fun payload() {
        val now = Instant.parse("2026-11-01T12:00:00Z")
        val payload = SseAlertBroadcaster.toPayload(
            Alert(
                id = "a1",
                kind = AlertKind.PROBE_RECOVERED,
                severity = Severity.INFO,
                service = "pdv",
                title = "pdv: normalizado",
                message = "ok",
                fingerprint = "probe:recovered:pdv:1",
                firstSeenAt = now,
                lastSeenAt = now,
            ),
        )
        assertEquals("a1", payload["id"])
        assertEquals("PROBE_RECOVERED", payload["kind"])
        assertEquals(false, payload["noisy"])
        assertEquals("2026-11-01T12:00:00Z", payload["lastSeenAt"])
    }
}
