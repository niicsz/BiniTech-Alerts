package com.binitech.alerts.domain

import java.time.Instant

enum class AlertKind { SLOW_REQUEST, PROBE_SLOW, PROBE_DOWN, PROBE_RECOVERED, TEST }

enum class Severity { CRITICAL, WARNING, INFO }

data class Alert(
    val id: String? = null,
    val kind: AlertKind,
    val severity: Severity,
    val service: String,
    val title: String,
    val message: String,
    val fingerprint: String,
    val method: String? = null,
    val path: String? = null,
    val status: Int? = null,
    val durationMs: Long? = null,
    val thresholdMs: Long? = null,
    val occurrences: Int = 1,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val acknowledgedAt: Instant? = null,
) {
    val acknowledged: Boolean
        get() = acknowledgedAt != null

    val noisy: Boolean
        get() = severity != Severity.INFO
}

data class SlowRequest(
    val service: String,
    val method: String,
    val path: String,
    val status: Int,
    val durationMs: Long,
    val occurredAt: Instant,
    val suppressed: Int = 0,
)

data class ProbeTarget(val name: String, val url: String)

data class ProbeResult(val target: ProbeTarget, val status: Int?, val durationMs: Long, val error: String?)

data class TargetStatus(
    val name: String,
    val url: String,
    val healthy: Boolean,
    val lastStatus: Int?,
    val lastDurationMs: Long?,
    val lastError: String?,
    val lastCheckedAt: Instant?,
)

class ResourceNotFoundException(message: String) : RuntimeException(message)
