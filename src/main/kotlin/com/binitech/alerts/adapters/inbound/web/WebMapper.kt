package com.binitech.alerts.adapters.inbound.web

import com.binitech.alerts.adapters.inbound.web.generated.model.AlertDTO
import com.binitech.alerts.adapters.inbound.web.generated.model.AlertKind
import com.binitech.alerts.adapters.inbound.web.generated.model.AlertSeverity
import com.binitech.alerts.adapters.inbound.web.generated.model.TargetStatusDTO
import com.binitech.alerts.domain.Alert
import com.binitech.alerts.domain.TargetStatus
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

object WebMapper {

    fun alert(alert: Alert) = AlertDTO(
        id = alert.id ?: "",
        kind = AlertKind.valueOf(alert.kind.name),
        severity = AlertSeverity.valueOf(alert.severity.name),
        service = alert.service,
        title = alert.title,
        message = alert.message,
        occurrences = alert.occurrences,
        firstSeenAt = offset(alert.firstSeenAt),
        lastSeenAt = offset(alert.lastSeenAt),
        acknowledged = alert.acknowledged,
        noisy = alert.noisy,
        method = alert.method,
        path = alert.path,
        status = alert.status,
        durationMs = alert.durationMs,
        thresholdMs = alert.thresholdMs,
        acknowledgedAt = alert.acknowledgedAt?.let(::offset),
    )

    fun target(status: TargetStatus) = TargetStatusDTO(
        name = status.name,
        url = status.url,
        healthy = status.healthy,
        lastStatus = status.lastStatus,
        lastDurationMs = status.lastDurationMs,
        lastError = status.lastError,
        lastCheckedAt = status.lastCheckedAt?.let(::offset),
    )

    private fun offset(instant: Instant): OffsetDateTime = instant.atOffset(ZoneOffset.UTC)
}
