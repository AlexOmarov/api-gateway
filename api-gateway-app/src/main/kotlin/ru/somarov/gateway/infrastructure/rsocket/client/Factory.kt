package ru.somarov.gateway.infrastructure.rsocket.client

import com.fasterxml.jackson.databind.ObjectMapper
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
import io.rsocket.transport.ClientTransport
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.*

object Factory {

    fun create(
        config: Config,
        mapper: ObjectMapper,
        meterRegistry: MeterRegistry,
        observationRegistry: ObservationRegistry
    ): RSocketClient {
        val connector = RSocketConnector.create().also {
            configure(it, config)
            addInterceptors(it, mapper, meterRegistry, observationRegistry)
        }

        return LoadbalanceRSocketClient.builder { getEndlessTarget(config.transport) }
            .connector(connector)
            .weightedLoadbalanceStrategy()
            .build()
    }

    private fun configure(connector: RSocketConnector, config: Config) {
        connector.resume(Resume().also { it.retry(config.resumption.retry) })

        connector.reconnect(config.reconnect.retry)


        connector.keepAlive(
            Duration.ofMillis(config.keepAlive.interval),
            Duration.ofMillis(config.keepAlive.maxLifeTime)
        )
    }

    private fun addInterceptors(
        connector: RSocketConnector,
        mapper: ObjectMapper,
        meterRegistry: MeterRegistry,
        observationRegistry: ObservationRegistry
    ) {
        connector.interceptors {
            it.forRequester(Interceptor(mapper))
            it.forRequester(MicrometerRSocketInterceptor(meterRegistry))
            it.forRequester(RSocketInterceptor { ObservationRequesterRSocketProxy(it, observationRegistry) })
        }
    }

    private fun getEndlessTarget(transport: ClientTransport) =
        Flux.just(LoadbalanceTarget.from(UUID.randomUUID().toString(), transport)).repeat()
}
