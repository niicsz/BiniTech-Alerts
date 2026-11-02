package com.binitech.alerts.application.ports.outbound

import com.binitech.alerts.domain.Alert
import com.binitech.alerts.domain.ProbeResult
import com.binitech.alerts.domain.ProbeTarget
import java.time.Instant

interface AlertRepositoryPort {
    fun save(alert: Alert): Alert

    fun findById(id: String): Alert?

    fun findOpenByFingerprint(fingerprint: String, since: Instant): Alert?

    fun recent(limit: Int): List<Alert>
}

interface AlertBroadcastPort {
    fun broadcast(alert: Alert, event: String)
}

interface ProbePort {
    fun check(target: ProbeTarget): ProbeResult
}
