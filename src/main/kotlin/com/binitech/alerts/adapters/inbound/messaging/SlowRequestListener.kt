package com.binitech.alerts.adapters.inbound.messaging

import com.binitech.alerts.application.ports.inbound.AlertUseCase
import com.binitech.alerts.config.RabbitConfiguration
import com.binitech.alerts.domain.SlowRequest
import java.time.Instant
import org.springframework.amqp.rabbit.annotation.RabbitListener

class SlowRequestListener(private val alerts: AlertUseCase) {

    @RabbitListener(queues = [RabbitConfiguration.SLOW_REQUESTS_QUEUE])
    fun onSlowRequest(message: SlowRequestMessage) {
        alerts.registerSlowRequest(message.toDomain())
    }

    data class SlowRequestMessage(
        val service: String? = null,
        val method: String? = null,
        val path: String? = null,
        val status: Int? = null,
        val durationMs: Long? = null,
        val occurredAt: Instant? = null,
        val suppressed: Int? = null,
    ) {
        fun toDomain() = SlowRequest(
            service = service ?: "desconhecido",
            method = method ?: "GET",
            path = path ?: "/",
            status = status ?: 0,
            durationMs = durationMs ?: 0,
            occurredAt = occurredAt ?: Instant.now(),
            suppressed = suppressed ?: 0,
        )
    }
}
