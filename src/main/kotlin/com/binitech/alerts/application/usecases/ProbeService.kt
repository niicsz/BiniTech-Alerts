package com.binitech.alerts.application.usecases

import com.binitech.alerts.application.ports.inbound.ProbeUseCase
import com.binitech.alerts.application.ports.outbound.ProbePort
import com.binitech.alerts.domain.Alert
import com.binitech.alerts.domain.AlertKind
import com.binitech.alerts.domain.ProbeResult
import com.binitech.alerts.domain.ProbeTarget
import com.binitech.alerts.domain.Severity
import com.binitech.alerts.domain.TargetStatus
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class ProbeService(
    private val probe: ProbePort,
    private val alerts: AlertService,
    private val targets: List<ProbeTarget>,
    private val clock: Clock,
    private val thresholdMs: Long,
    private val confirmations: Int = 2,
) : ProbeUseCase {

    private enum class Problem { NONE, SLOW, DOWN }

    private class State {
        var problem = Problem.NONE
        var streak = 0
        var alerted = false
        var status: TargetStatus? = null
    }

    private val states = ConcurrentHashMap<String, State>()

    override fun probeAll(): List<TargetStatus> = targets.map { evaluate(probe.check(it)) }

    override fun statuses(): List<TargetStatus> = targets.map { target ->
        states[target.name]?.status ?: TargetStatus(target.name, target.url, true, null, null, null, null)
    }

    private fun classify(result: ProbeResult): Problem = when {
        result.error != null || result.status == null || result.status >= SERVER_ERROR -> Problem.DOWN
        result.durationMs > thresholdMs -> Problem.SLOW
        else -> Problem.NONE
    }

    private fun evaluate(result: ProbeResult): TargetStatus {
        val target = result.target
        val state = states.computeIfAbsent(target.name) { State() }
        val problem = classify(result)
        val now = clock.instant()
        val status = TargetStatus(
            target.name, target.url, problem == Problem.NONE, result.status, result.durationMs, result.error, now,
        )
        synchronized(state) {
            state.status = status
            if (problem == Problem.NONE) {
                if (state.alerted) {
                    alerts.raise(recovered(target, result, now))
                }
                state.problem = Problem.NONE
                state.streak = 0
                state.alerted = false
            } else {
                state.streak = if (state.problem == problem) state.streak + 1 else 1
                state.problem = problem
                if (state.streak >= confirmations) {
                    alerts.raise(if (problem == Problem.DOWN) down(target, result, now) else slow(target, result, now))
                    state.alerted = true
                }
            }
        }
        return status
    }

    private fun base(target: ProbeTarget, result: ProbeResult, now: Instant) = Alert(
        kind = AlertKind.PROBE_SLOW,
        severity = Severity.WARNING,
        service = target.name,
        title = "",
        message = "",
        fingerprint = "",
        method = "GET",
        path = target.url,
        status = result.status,
        durationMs = result.durationMs,
        thresholdMs = thresholdMs,
        firstSeenAt = now,
        lastSeenAt = now,
    )

    private fun slow(target: ProbeTarget, result: ProbeResult, now: Instant) = base(target, result, now).copy(
        title = "${target.name}: lento (${AlertService.seconds(result.durationMs)})",
        message = "${target.url} respondeu ${result.status} em ${result.durationMs} ms " +
            "em $confirmations verificações seguidas (limite $thresholdMs ms).",
        fingerprint = "probe:slow:${target.name}",
    )

    private fun down(target: ProbeTarget, result: ProbeResult, now: Instant) = base(target, result, now).copy(
        kind = AlertKind.PROBE_DOWN,
        severity = Severity.CRITICAL,
        title = "${target.name}: fora do ar",
        message = "${target.url} " + (result.error?.let { "não respondeu ($it)" } ?: "respondeu ${result.status}") +
            " em $confirmations verificações seguidas.",
        fingerprint = "probe:down:${target.name}",
    )

    private fun recovered(target: ProbeTarget, result: ProbeResult, now: Instant) = base(target, result, now).copy(
        kind = AlertKind.PROBE_RECOVERED,
        severity = Severity.INFO,
        title = "${target.name}: normalizado",
        message = "${target.url} voltou a responder ${result.status} em ${result.durationMs} ms.",
        fingerprint = "probe:recovered:${target.name}:${now.toEpochMilli()}",
    )

    companion object {
        private const val SERVER_ERROR = 500
    }
}
