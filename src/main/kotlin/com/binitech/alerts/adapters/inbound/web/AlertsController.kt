package com.binitech.alerts.adapters.inbound.web

import com.binitech.alerts.adapters.inbound.web.generated.api.AlertsApi
import com.binitech.alerts.adapters.inbound.web.generated.model.AlertDTO
import com.binitech.alerts.adapters.inbound.web.generated.model.TargetStatusDTO
import com.binitech.alerts.adapters.outbound.push.SseAlertBroadcaster
import com.binitech.alerts.application.ports.inbound.AlertUseCase
import com.binitech.alerts.application.ports.inbound.ProbeUseCase
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
class AlertsController(
    private val alerts: AlertUseCase,
    private val probes: ProbeUseCase,
    private val stream: SseAlertBroadcaster,
) : AlertsApi {

    override fun listAlerts(limit: Int): ResponseEntity<List<AlertDTO>> =
        ResponseEntity.ok(alerts.recent(limit).map(WebMapper::alert))

    override fun acknowledgeAlert(id: String): ResponseEntity<AlertDTO> =
        ResponseEntity.ok(WebMapper.alert(alerts.acknowledge(id)))

    override fun sendTestAlert(): ResponseEntity<AlertDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(WebMapper.alert(alerts.sendTest()))

    override fun listTargets(): ResponseEntity<List<TargetStatusDTO>> =
        ResponseEntity.ok(probes.statuses().map(WebMapper::target))

    @GetMapping("/api/alerts/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamAlerts(): SseEmitter = stream.subscribe()
}
