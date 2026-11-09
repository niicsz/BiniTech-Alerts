package com.binitech.alerts.config

import com.binitech.alerts.adapters.inbound.messaging.SlowRequestListener
import com.binitech.alerts.adapters.inbound.scheduling.ProbeScheduler
import com.binitech.alerts.adapters.inbound.web.ApiTokenFilter
import com.binitech.alerts.adapters.outbound.http.HttpProbeAdapter
import com.binitech.alerts.adapters.outbound.persistence.MongoAlertRepository
import com.binitech.alerts.adapters.outbound.push.SseAlertBroadcaster
import com.binitech.alerts.application.ports.inbound.AlertUseCase
import com.binitech.alerts.application.ports.inbound.ProbeUseCase
import com.binitech.alerts.application.ports.outbound.AlertRepositoryPort
import com.binitech.alerts.application.usecases.AlertService
import com.binitech.alerts.application.usecases.ProbeService
import java.time.Clock
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.data.mongodb.core.MongoTemplate
import tools.jackson.databind.ObjectMapper

@Configuration
class BeanConfiguration {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun alertRepository(mongo: MongoTemplate): MongoAlertRepository =
        MongoAlertRepository(mongo).also { it.ensureIndexes() }

    @Bean
    fun alertStream(objectMapper: ObjectMapper) = SseAlertBroadcaster(objectMapper)

    @Bean
    fun alertService(
        alertRepository: AlertRepositoryPort,
        alertStream: SseAlertBroadcaster,
        clock: Clock,
        properties: AlertsProperties,
    ) = AlertService(alertRepository, alertStream, clock, properties.thresholdMs, properties.cooldown)

    @Bean
    fun probeService(alertService: AlertService, clock: Clock, properties: AlertsProperties): ProbeService =
        ProbeService(
            HttpProbeAdapter(properties.probeTimeout),
            alertService,
            properties.targets(),
            clock,
            properties.thresholdMs,
            properties.probeConfirmations,
        )

    @Bean
    fun slowRequestListener(alerts: AlertUseCase) = SlowRequestListener(alerts)

    @Bean
    fun probeScheduler(probes: ProbeUseCase, alertStream: SseAlertBroadcaster) = ProbeScheduler(probes, alertStream)

    @Bean
    fun apiTokenFilter(properties: AlertsProperties): FilterRegistrationBean<ApiTokenFilter> =
        FilterRegistrationBean(ApiTokenFilter(properties.apiToken)).apply {
            order = Ordered.HIGHEST_PRECEDENCE
            addUrlPatterns("/api/*")
        }
}
