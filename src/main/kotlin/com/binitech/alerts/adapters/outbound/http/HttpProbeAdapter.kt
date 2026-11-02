package com.binitech.alerts.adapters.outbound.http

import com.binitech.alerts.application.ports.outbound.ProbePort
import com.binitech.alerts.domain.ProbeResult
import com.binitech.alerts.domain.ProbeTarget
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

class HttpProbeAdapter(private val timeout: Duration) : ProbePort {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun check(target: ProbeTarget): ProbeResult {
        val request = HttpRequest.newBuilder(URI.create(target.url))
            .timeout(timeout)
            .header("User-Agent", "BiniTech-Alerts/1.0")
            .GET()
            .build()
        val started = System.nanoTime()
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.discarding())
            ProbeResult(target, response.statusCode(), elapsed(started), null)
        } catch (e: HttpTimeoutException) {
            ProbeResult(target, null, elapsed(started), "timeout de ${timeout.toSeconds()} s")
        } catch (e: IOException) {
            ProbeResult(target, null, elapsed(started), e.javaClass.simpleName)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            ProbeResult(target, null, elapsed(started), "interrompido")
        }
    }

    private fun elapsed(started: Long): Long = Duration.ofNanos(System.nanoTime() - started).toMillis()
}
