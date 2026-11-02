package com.binitech.alerts.adapters.inbound.scheduling

import com.binitech.alerts.adapters.outbound.push.SseAlertBroadcaster
import com.binitech.alerts.application.ports.inbound.ProbeUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class ProbeScheduler(private val probes: ProbeUseCase, private val stream: SseAlertBroadcaster) {

    private val log = LoggerFactory.getLogger(ProbeScheduler::class.java)

    @Scheduled(fixedDelayString = "\${alerts.probe-interval:30s}", initialDelayString = "\${alerts.probe-initial-delay:20s}")
    fun probe() {
        try {
            probes.probeAll()
        } catch (e: RuntimeException) {
            log.error("Falha no ciclo de sondagem: {}", e.message, e)
        }
    }

    @Scheduled(fixedDelayString = "\${alerts.heartbeat-interval:20s}")
    fun heartbeat() {
        stream.heartbeat()
    }
}
