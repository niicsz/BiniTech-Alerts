package com.binitech.alerts

import com.binitech.alerts.application.ports.outbound.AlertBroadcastPort
import com.binitech.alerts.application.ports.outbound.AlertRepositoryPort
import com.binitech.alerts.application.ports.outbound.ProbePort
import com.binitech.alerts.domain.Alert
import com.binitech.alerts.domain.ProbeResult
import com.binitech.alerts.domain.ProbeTarget
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.ArrayDeque
import java.util.UUID

class InMemoryAlerts : AlertRepositoryPort {
    val items = linkedMapOf<String, Alert>()

    override fun save(alert: Alert): Alert {
        val saved = if (alert.id == null) alert.copy(id = UUID.randomUUID().toString()) else alert
        items[saved.id!!] = saved
        return saved
    }

    override fun findById(id: String): Alert? = items[id]

    override fun findOpenByFingerprint(fingerprint: String, since: Instant): Alert? = items.values
        .filter { it.fingerprint == fingerprint && it.acknowledgedAt == null && !it.lastSeenAt.isBefore(since) }
        .maxByOrNull { it.lastSeenAt }

    override fun recent(limit: Int): List<Alert> = items.values.sortedByDescending { it.lastSeenAt }.take(limit)
}

class RecordingBroadcast : AlertBroadcastPort {
    val events = mutableListOf<Pair<String, Alert>>()

    override fun broadcast(alert: Alert, event: String) {
        events += event to alert
    }

    fun noisyAlerts() = events.filter { it.first == "alert" && it.second.noisy }
}

class ScriptedProbe : ProbePort {
    val results = ArrayDeque<(ProbeTarget) -> ProbeResult>()

    override fun check(target: ProbeTarget): ProbeResult = results.poll()?.invoke(target)
        ?: ProbeResult(target, 200, 100, null)

    fun ok(ms: Long = 100) = results.add { ProbeResult(it, 200, ms, null) }

    fun slow(ms: Long = 1500) = results.add { ProbeResult(it, 200, ms, null) }

    fun down() = results.add { ProbeResult(it, null, 10_000, "timeout de 10 s") }
}

class MutableClock(var now: Instant = Instant.parse("2026-11-01T12:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}
