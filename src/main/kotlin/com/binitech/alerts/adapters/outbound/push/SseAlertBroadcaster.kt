package com.binitech.alerts.adapters.outbound.push

import com.binitech.alerts.application.ports.outbound.AlertBroadcastPort
import com.binitech.alerts.domain.Alert
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class SseAlertBroadcaster(private val json: ObjectMapper) : AlertBroadcastPort {

    private val log = LoggerFactory.getLogger(SseAlertBroadcaster::class.java)
    private val subscribers = CopyOnWriteArrayList<SseEmitter>()

    fun subscribe(): SseEmitter {
        val emitter = SseEmitter(0L)
        subscribers.add(emitter)
        emitter.onCompletion { subscribers.remove(emitter) }
        emitter.onTimeout { subscribers.remove(emitter) }
        emitter.onError { subscribers.remove(emitter) }
        send(emitter, SseEmitter.event().name("ready").data("{}", MediaType.APPLICATION_JSON))
        return emitter
    }

    fun subscriberCount(): Int = subscribers.size

    override fun broadcast(alert: Alert, event: String) {
        val payload = json.writeValueAsString(toPayload(alert))
        subscribers.forEach { emitter ->
            send(emitter, SseEmitter.event().name(event).id(alert.id ?: "").data(payload, MediaType.APPLICATION_JSON))
        }
        log.info("Alerta {} enviado para {} app(s): {}", event, subscribers.size, alert.title)
    }

    fun heartbeat() {
        subscribers.forEach { send(it, SseEmitter.event().comment("ping")) }
    }

    private fun send(emitter: SseEmitter, event: SseEmitter.SseEventBuilder) {
        try {
            emitter.send(event)
        } catch (e: IOException) {
            subscribers.remove(emitter)
            emitter.completeWithError(e)
        } catch (e: IllegalStateException) {
            subscribers.remove(emitter)
        }
    }

    companion object {
        fun toPayload(alert: Alert): Map<String, Any?> = mapOf(
            "id" to alert.id,
            "kind" to alert.kind.name,
            "severity" to alert.severity.name,
            "service" to alert.service,
            "title" to alert.title,
            "message" to alert.message,
            "method" to alert.method,
            "path" to alert.path,
            "status" to alert.status,
            "durationMs" to alert.durationMs,
            "thresholdMs" to alert.thresholdMs,
            "occurrences" to alert.occurrences,
            "firstSeenAt" to alert.firstSeenAt.toString(),
            "lastSeenAt" to alert.lastSeenAt.toString(),
            "acknowledged" to alert.acknowledged,
            "acknowledgedAt" to alert.acknowledgedAt?.toString(),
            "noisy" to alert.noisy,
        )
    }
}
