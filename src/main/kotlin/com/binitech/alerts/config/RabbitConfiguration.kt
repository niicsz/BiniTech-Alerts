package com.binitech.alerts.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitConfiguration {

    @Bean
    fun observabilityExchange() = TopicExchange(OBSERVABILITY_EXCHANGE, true, false)

    @Bean
    fun slowRequestsQueue(): Queue = QueueBuilder.durable(SLOW_REQUESTS_QUEUE)
        .ttl(MESSAGE_TTL_MS)
        .maxLength(MAX_QUEUED)
        .build()

    @Bean
    fun slowRequestsBinding(slowRequestsQueue: Queue, observabilityExchange: TopicExchange): Binding =
        BindingBuilder.bind(slowRequestsQueue).to(observabilityExchange).with(SLOW_REQUEST_KEY)

    @Bean
    fun alertsMessageConverter(objectMapper: ObjectMapper) = Jackson2JsonMessageConverter(objectMapper)

    companion object {
        const val OBSERVABILITY_EXCHANGE = "binitech.observability"
        const val SLOW_REQUEST_KEY = "request.slow"
        const val SLOW_REQUESTS_QUEUE = "alerts.requests.slow"
        private const val MESSAGE_TTL_MS = 600_000
        private const val MAX_QUEUED = 10_000L
    }
}
