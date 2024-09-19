package ru.somarov.gateway.infrastructure.lib.rsocket.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.rsocket.core.RSocketClient
import io.rsocket.core.RSocketConnector
import io.rsocket.core.Resume
import io.rsocket.loadbalance.LoadbalanceRSocketClient
import io.rsocket.loadbalance.LoadbalanceTarget
import io.rsocket.micrometer.MicrometerRSocketInterceptor
import io.rsocket.micrometer.observation.ObservationRequesterRSocketProxy
import io.rsocket.plugins.RSocketInterceptor
import io.rsocket.transport.netty.client.WebsocketClientTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactor.asFlux
import java.net.URI
import java.time.Duration.ofMillis
import java.util.UUID

fun create(
    config: Config,
    meterRegistry: MeterRegistry,
    observationRegistry: ObservationRegistry,
    mapper: ObjectMapper
): RSocketClient {
    val sources = mutableListOf<LoadbalanceTarget>()
    val log = logger { }
    repeat(config.pool.size) { sources.add(createTarget(config.host)) }

    return LoadbalanceRSocketClient
        .builder(createInfiniteSources(config, sources, log))
        .connector(createConnector(config, meterRegistry, observationRegistry, mapper))
        .loadbalanceStrategy(config.loadBalanceStrategy)
        .build()
}

private fun createInfiniteSources(config: Config, sources: MutableList<LoadbalanceTarget>, log: KLogger) = flow {
    while (true) {
        delay(config.pool.interval)
        updateConnectionPool(config, sources, log)
        emit(sources)
    }
}
    .asFlux()
    .doOnNext { log.info { "Got next list ${it.map { it.key }} for load balancing" } }

private fun updateConnectionPool(config: Config, sources: MutableList<LoadbalanceTarget>, log: KLogger) {
    val new = createTarget(config.host)
    val old = sources.set(sources.indexOf(sources.random()), new)
    log.info { "RSocket client ${config.name} updated: ${old.key} is replaced by ${new.key}" }
}

private fun createConnector(
    config: Config,
    meterRegistry: MeterRegistry,
    observationRegistry: ObservationRegistry,
    mapper: ObjectMapper
): RSocketConnector {
    val connector = RSocketConnector.create()

    config.resumption?.let { connector.resume(Resume().retry(config.resumption.retry)) }
    connector.reconnect(config.reconnect.retry)
    connector.keepAlive(ofMillis(config.keepAlive.interval), ofMillis(config.keepAlive.maxLifeTime))

    connector.interceptors { con ->
        con.forRequester(LoggingInterceptor(mapper))
        con.forRequester(MicrometerRSocketInterceptor(meterRegistry))
        con.forRequester(RSocketInterceptor { ObservationRequesterRSocketProxy(it, observationRegistry) })
    }

    return connector
}

private fun createTarget(host: String) = LoadbalanceTarget.from(
    UUID.randomUUID().toString(),
    WebsocketClientTransport.create(URI(host))
)
