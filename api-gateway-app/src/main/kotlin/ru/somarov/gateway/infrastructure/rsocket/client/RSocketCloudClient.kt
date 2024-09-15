package ru.somarov.gateway.infrastructure.rsocket.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
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
import io.rsocket.transport.netty.client.WebsocketClientTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.somarov.gateway.infrastructure.rsocket.payload.deserialize
import ru.somarov.gateway.infrastructure.rsocket.payload.toJavaPayload
import ru.somarov.gateway.infrastructure.rsocket.payload.toKotlinPayload
import java.net.URI
import java.time.Duration.ofMillis
import java.util.*
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalMetadataApi::class)
class RSocketCloudClient(
    private val config: Config,
    private val mapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
    private val observationRegistry: ObservationRegistry,
    override val coroutineContext: CoroutineContext
) : RSocket {
    private val log = logger { }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val sources = mutableListOf<LoadbalanceTarget>()

    // TODO: for some reason here dispose is called on rsocket, but stream continues. Why
    private val client = LoadbalanceRSocketClient
        .builder(
            flow {
                while (true) {
                    delay(1000)
                    emit(sources)
                }
            }.asFlux().doOnNext { log.info { "Got next element $it for load balancing" } }
        )
        .connector(createConnector())
        .loadbalanceStrategy(config.loadBalanceStrategy)
        .build()

    init {
        repeat(config.pool.size) { sources.add(createTarget(config.host)) }
        scope.launch {
            while (true) {
                delay(config.pool.interval)
                updateConnectionPool()
            }
        }
    }

    override suspend fun requestResponse(payload: Payload): Payload {
        return withContext(coroutineContext) {
            client.requestResponse(Mono.just(payload.toJavaPayload())).contextCapture()
                .onErrorResume { if (it is CancellationException) retryRequest(it, payload, 1) else Mono.error(it) }
                .awaitSingle()
                .toKotlinPayload()
        }
    }

    override fun requestStream(payload: Payload): Flow<Payload> {
        return client.requestStream(Mono.just(payload.toJavaPayload()))
            .contextCapture()
            .contextWrite { it.putAllMap(foldContext()) } // Pass values to the Reactor Context here
            .onErrorResume { if (it is CancellationException) retryStream(it, payload, 1) else Flux.error(it) }
            .asFlow()
            .map { it.toKotlinPayload() }
    }

    override suspend fun fireAndForget(payload: Payload) {
        return withContext(coroutineContext) {
            client.fireAndForget(Mono.just(payload.toJavaPayload())).contextCapture()
                .onErrorResume { if (it is CancellationException) retryFire(it, payload, 1) else Mono.error(it) }
                .awaitSingleOrNull()
        }
    }

    override fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> {
        val unifiedFlux = flowOf(initPayload.toJavaPayload())
            .onCompletion { if (it == null) emitAll(payloads.map { it.toJavaPayload() }) }
            .asFlux()

        return client.requestChannel(unifiedFlux)
            .contextCapture()
            .contextWrite { it.putAllMap(foldContext()) } // Pass values to the Reactor Context here
            .onErrorResume { if (it is CancellationException) retryChannel(it, unifiedFlux, 1) else Flux.error(it) }
            .asFlow()
            .map { it.toKotlinPayload() }
    }

    private fun foldContext() = coroutineContext
        .fold(mutableMapOf<String, Any?>()) { acc, el -> acc.also { it[el.key.toString()] = el } }

    private fun updateConnectionPool() {
        val new = createTarget(config.host)
        val old = sources.set(sources.indexOf(sources.random()), new)
        log.info { "RSocket client ${config.name} updated: ${old.key} is replaced by ${new.key}" }
    }

    private fun createConnector(): RSocketConnector {
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

    private fun retryRequest(throwable: Throwable, payload: Payload, attempt: Int): Mono<io.rsocket.Payload> {
        log.error(throwable) {
            "Got error while processing request response, " +
                "payload ${payload.toJavaPayload().deserialize(mapper)}, " +
                "attempt $attempt"
        }
        return if (attempt >= config.reconnect.attempts) {
            Mono.error(throwable)
        } else {
            val newCall = client.requestResponse(Mono.just(payload.toJavaPayload()))
                .contextCapture()
                .onErrorResume { retryRequest(it, payload, attempt + 1) }

            Mono.delay(ofMillis(config.reconnect.delay)).then(newCall)
        }
    }

    private fun retryFire(throwable: Throwable, payload: Payload, attempt: Int): Mono<Void> {
        return if (attempt >= config.reconnect.attempts) {
            Mono.error(throwable)
        } else {
            val newCall = client.fireAndForget(Mono.just(payload.toJavaPayload()))
                .contextCapture()
                .onErrorResume { retryFire(it, payload, attempt + 1) }

            Mono.delay(ofMillis(config.reconnect.delay)).then(newCall)
        }
    }

    private fun retryStream(throwable: Throwable, payload: Payload, attempt: Int): Flux<io.rsocket.Payload> {
        return if (attempt >= config.reconnect.attempts) {
            Flux.error(throwable)
        } else {
            client.requestStream(Mono.just(payload.toJavaPayload()))
                .contextCapture()
                .contextWrite { it.putAllMap(foldContext()) } // Pass values to the Reactor Context here
                .onErrorResume { retryStream(it, payload, attempt + 1) }
        }
    }

    private fun retryChannel(ex: Throwable, flux: Flux<io.rsocket.Payload>, attempt: Int): Flux<io.rsocket.Payload> {
        return if (attempt >= config.reconnect.attempts) {
            Flux.error(ex)
        } else {
            client.requestChannel(flux)
                .contextCapture()
                .contextWrite { it.putAllMap(foldContext()) } // Pass values to the Reactor Context here
                .onErrorResume { retryChannel(it, flux, attempt + 1) }
        }
    }
}
