package com.binitech.alerts.application.ports.inbound

import com.binitech.alerts.domain.Alert
import com.binitech.alerts.domain.SlowRequest
import com.binitech.alerts.domain.TargetStatus

interface AlertUseCase {
    fun registerSlowRequest(request: SlowRequest): Alert?

    fun recent(limit: Int): List<Alert>

    fun acknowledge(id: String): Alert

    fun sendTest(): Alert
}

interface ProbeUseCase {
    fun probeAll(): List<TargetStatus>

    fun statuses(): List<TargetStatus>
}
