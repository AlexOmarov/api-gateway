package ru.somarov.gateway.infrastructure.rsocket.client

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.rsocket.core.RSocketClient
import io.rsocket.core.RSocketConnector
import io.rsocket.core.Resume
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.loadbalance.LoadbalanceRSocketClient
import io.rsocket.loadbalance.LoadbalanceTarget
import io.rsocket.micrometer.MicrometerRSocketInterceptor
import io.rsocket.micrometer.observation.ObservationRequesterRSocketProxy
import io.rsocket.plugins.RSocketInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingle
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.somarov.gateway.infrastructure.rsocket.payload.toJavaPayload
import ru.somarov.gateway.infrastructure.rsocket.payload.toKotlinPayload
import java.time.Duration.ofMillis
import java.util.*
import kotlin.coroutines.CoroutineContext

class Client(private val config: Config, override val coroutineContext: CoroutineContext) : RSocket {
    private val logger = logger {}

    private val scope = CoroutineScope(Dispatchers.IO)

    private var current: RSocketClient
    private var old: RSocketClient? = null

    init {
        current = create()

        scope.launch {
            while (true) {
                delay(config.refreshInterval)

                logger.info { "Refreshing rsocket pool" }

                val new = create()
                old = current
                current = new
                old?.dispose()

                logger.info { "Pool is refreshed" }
            }
        }
    }

    @OptIn(ExperimentalMetadataApi::class)
    override suspend fun requestResponse(payload: Payload): Payload {
        return current.requestResponse(Mono.just(payload.toJavaPayload())).awaitSingle().toKotlinPayload()
    }

    private fun create(): RSocketClient {
        val connector = RSocketConnector.create()

        connector.resume(Resume().also { it.retry(config.resumption.retry) })
        connector.reconnect(config.reconnect.retry)
        connector.keepAlive(ofMillis(config.keepAlive.interval), ofMillis(config.keepAlive.maxLifeTime))

        connector.interceptors {
            it.forRequester(Interceptor())
            it.forRequester(MicrometerRSocketInterceptor(config.meterRegistry))
            it.forRequester(RSocketInterceptor { ObservationRequesterRSocketProxy(it, config.observationRegistry) })
        }

        val source = mutableListOf<LoadbalanceTarget>()
        repeat(config.poolSize) { source.add(LoadbalanceTarget.from(UUID.randomUUID().toString(), config.transport)) }

        return LoadbalanceRSocketClient.builder { Flux.just(source).repeat() }
            .connector(connector)
            .weightedLoadbalanceStrategy()
            .build()
    }
}
