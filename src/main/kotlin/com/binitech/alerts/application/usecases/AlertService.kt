package com.binitech.alerts.application.usecases

import com.binitech.alerts.application.ports.inbound.AlertUseCase
import com.binitech.alerts.application.ports.outbound.AlertBroadcastPort
import com.binitech.alerts.application.ports.outbound.AlertRepositoryPort
import com.binitech.alerts.domain.Alert
import com.binitech.alerts.domain.AlertKind
import com.binitech.alerts.domain.ResourceNotFoundException
import com.binitech.alerts.domain.Severity
import com.binitech.alerts.domain.SlowRequest
import java.time.Clock
import java.time.Duration
import java.util.Locale
import org.slf4j.LoggerFactory

class AlertService(
    private val alerts: AlertRepositoryPort,
    private val broadcast: AlertBroadcastPort,
    private val clock: Clock,
    private val thresholdMs: Long,
    private val cooldown: Duration,
) : AlertUseCase {

    private val log = LoggerFactory.getLogger(AlertService::class.java)

    override fun registerSlowRequest(request: SlowRequest): Alert? {
        if (request.durationMs < thresholdMs) {
            return null
        }
        val critical = request.durationMs >= thresholdMs * CRITICAL_FACTOR || request.status >= SERVER_ERROR
        return raise(
            Alert(
                kind = AlertKind.SLOW_REQUEST,
                severity = if (critical) Severity.CRITICAL else Severity.WARNING,
                service = request.service,
                title = "${request.service}: resposta lenta (${seconds(request.durationMs)})",
                message = "${request.method} ${request.path} respondeu ${request.status} em " +
                    "${request.durationMs} ms (limite $thresholdMs ms).",
                fingerprint = "slow:${request.service}:${request.method} ${request.path}",
                method = request.method,
                path = request.path,
                status = request.status,
                durationMs = request.durationMs,
                thresholdMs = thresholdMs,
                occurrences = 1 + request.suppressed,
                firstSeenAt = request.occurredAt,
                lastSeenAt = request.occurredAt,
            ),
        )
    }

    fun raise(candidate: Alert): Alert {
        val open = alerts.findOpenByFingerprint(candidate.fingerprint, clock.instant().minus(cooldown))
        if (open != null) {
            val updated = alerts.save(
                open.copy(
                    occurrences = open.occurrences + candidate.occurrences,
                    lastSeenAt = maxOf(open.lastSeenAt, candidate.lastSeenAt),
                    durationMs = listOfNotNull(open.durationMs, candidate.durationMs).maxOrNull(),
                    severity = minOf(open.severity, candidate.severity),
                ),
            )
            broadcast.broadcast(updated, EVENT_UPDATE)
            return updated
        }
        val saved = alerts.save(candidate)
        log.warn("Alerta {}: {}", saved.severity, saved.title)
        broadcast.broadcast(saved, EVENT_ALERT)
        return saved
    }

    override fun recent(limit: Int): List<Alert> = alerts.recent(limit.coerceIn(1, MAX_LIMIT))

    override fun acknowledge(id: String): Alert {
        val alert = alerts.findById(id) ?: throw ResourceNotFoundException("Alerta não encontrado.")
        if (alert.acknowledged) {
            return alert
        }
        val saved = alerts.save(alert.copy(acknowledgedAt = clock.instant()))
        broadcast.broadcast(saved, EVENT_UPDATE)
        return saved
    }

    override fun sendTest(): Alert {
        val now = clock.instant()
        val saved = alerts.save(
            Alert(
                kind = AlertKind.TEST,
                severity = Severity.WARNING,
                service = "alerts",
                title = "Alerta de teste",
                message = "Se o alarme tocou, o app está pronto para receber os alertas de produção.",
                fingerprint = "test:${now.toEpochMilli()}",
                firstSeenAt = now,
                lastSeenAt = now,
            ),
        )
        broadcast.broadcast(saved, EVENT_ALERT)
        return saved
    }

    companion object {
        const val EVENT_ALERT = "alert"
        const val EVENT_UPDATE = "update"
        private const val CRITICAL_FACTOR = 5
        private const val SERVER_ERROR = 500
        private const val MAX_LIMIT = 200

        fun seconds(ms: Long): String =
            String.format(Locale.forLanguageTag("pt-BR"), "%.1f s", ms / MILLIS_PER_SECOND)

        private const val MILLIS_PER_SECOND = 1000.0
    }
}
