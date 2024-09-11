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
import io.rsocket.transport.netty.client.WebsocketClientTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.somarov.gateway.infrastructure.rsocket.payload.toJavaPayload
import ru.somarov.gateway.infrastructure.rsocket.payload.toKotlinPayload
import java.net.URI
import java.time.Duration
import java.time.Duration.ofMillis
import java.util.*
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalMetadataApi::class)
class RSocketCloudClient(private val config: Config, override val coroutineContext: CoroutineContext) : RSocket {
    private val log = logger { }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val sources = mutableListOf<LoadbalanceTarget>()

    private var client: RSocketClient = createClient()

    init {
        repeat(config.poolSize) { sources.add(createTarget(config.host)) }
        scope.launch {
            while (true) {
                delay(config.refreshInterval)
                val new = createTarget(config.host)
                val old = sources.set(sources.indexOf(sources.random()), new)
                log.info { "RSocket client ${config.name} updated: ${old.key} is replaced by ${new.key}" }
            }
        }
    }

    //  TODO: when load balance target is replaced corresponding RSocket is disposed.
    //  So if there were active connections using this RSocket instance, then it will be lost.
    //  So we need to retry such connections on next request.
    //  Either it will be request response or flow. Think of mono/flux retries (seems like it uses same disposed rsocket)
    //  Think of wrapper retries for each mono or flow call, mono as wrapper, flow as retryWhen and custom call to current client
    override suspend fun requestResponse(payload: Payload): Payload {
        return client.requestResponse(Mono.just(payload.toJavaPayload()))
            .contextCapture()
            .awaitSingle()
            .toKotlinPayload()
    }

    override fun requestStream(payload: Payload): Flow<Payload> {
        return client.requestStream(Mono.just(payload.toJavaPayload()))
            .contextCapture()
            .asFlow()
            .map { it.toKotlinPayload() }
    }

    private fun createClient(): RSocketClient {
        val connector = RSocketConnector.create()

        config.resumption?.let { connector.resume(Resume().retry(config.resumption.retry)) }
        connector.reconnect(config.reconnect.retry)
        connector.keepAlive(ofMillis(config.keepAlive.interval), ofMillis(config.keepAlive.maxLifeTime))

        connector.interceptors { con ->
            con.forRequester(Interceptor())
            con.forRequester(MicrometerRSocketInterceptor(config.meterRegistry))
            con.forRequester(RSocketInterceptor { ObservationRequesterRSocketProxy(it, config.observationRegistry) })
        }

        return LoadbalanceRSocketClient
            .builder(
                Flux.generate { it.next(sources) }
                    .delayElements(Duration.ofSeconds(2))
            )
            .connector(connector)
            .loadbalanceStrategy(config.loadBalanceStrategy)
            .build()
    }

    private fun createTarget(host: String) = LoadbalanceTarget.from(
        UUID.randomUUID().toString(),
        WebsocketClientTransport.create(URI(host))
    )
}
