package com.binitech.alerts.config

import com.binitech.alerts.domain.ProbeTarget
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("alerts")
data class AlertsProperties(
    val apiToken: String,
    val thresholdMs: Long = 1000,
    val cooldown: Duration = Duration.ofMinutes(5),
    val probeTargets: String = "",
    val probeTimeout: Duration = Duration.ofSeconds(10),
    val probeConfirmations: Int = 2,
) {
    fun targets(): List<ProbeTarget> = probeTargets.split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { entry ->
            val separator = entry.indexOf('=')
            require(separator > 0) { "ALERTS_PROBE_TARGETS deve ser nome=url separados por vírgula." }
            ProbeTarget(entry.substring(0, separator).trim(), entry.substring(separator + 1).trim())
        }
}
