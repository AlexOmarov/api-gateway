package ru.somarov.gateway.infrastructure.lib.client.rsocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readBytes
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.util.ByteBufPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.withContext
import reactor.core.publisher.Mono
import ru.somarov.gateway.infrastructure.lib.observability.ObservabilityRegistry
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalMetadataApi::class)
class Client(
    config: Config,
    mapper: ObjectMapper,
    registry: ObservabilityRegistry,
    override val coroutineContext: CoroutineContext
) : RSocket {
    private val logger = logger { }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var client = RSocketClientFactory.create(config, registry, mapper)

    init {
        scope.launch {
            delay(config.pool.interval)
            while (true) {
                refreshPool(config, registry, mapper)
            }
        }
    }

    override suspend fun requestResponse(payload: Payload): Payload {
        return withContext(coroutineContext) {
            client.requestResponse(Mono.just(payload.toJavaPayload()))
                .contextCapture()
                .contextWrite { it.putAllMap(foldContext()) }
                .awaitSingle()
                .toKotlinPayload()
        }
    }

    override fun requestStream(payload: Payload): Flow<Payload> {
        return client.requestStream(Mono.just(payload.toJavaPayload()))
            .contextCapture()
            .contextWrite { it.putAllMap(foldContext()) }
            .asFlow()
            .map { it.toKotlinPayload() }
    }

    override suspend fun fireAndForget(payload: Payload) {
        return withContext(coroutineContext) {
            client.fireAndForget(Mono.just(payload.toJavaPayload()))
                .contextCapture()
                .contextWrite { it.putAllMap(foldContext()) }
                .awaitSingleOrNull()
        }
    }

    override fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> {
        val unifiedFlux = flowOf(initPayload.toJavaPayload())
            .onCompletion { if (it == null) emitAll(payloads.map { it.toJavaPayload() }) }
            .asFlux()

        return client.requestChannel(unifiedFlux)
            .contextCapture()
            .contextWrite { it.putAllMap(foldContext()) }
            .asFlow()
            .map { it.toKotlinPayload() }
    }

    override suspend fun metadataPush(metadata: ByteReadPacket) {
        client.metadataPush(Mono.just(ByteBufPayload.create(ByteArray(0), metadata.readBytes())))
            .contextCapture()
            .contextWrite { it.putAllMap(foldContext()) }
            .awaitSingleOrNull()
    }

    private fun foldContext() = coroutineContext
        .fold(mutableMapOf<String, Any?>()) { acc, el -> acc.also { it[el.key.toString()] = el } }

    private suspend fun refreshPool(config: Config, registry: ObservabilityRegistry, mapper: ObjectMapper) {
        logger.info { "RSocket ${config.name} update iteration launched" }
        val new = RSocketClientFactory.create(config, registry, mapper)
        val old = client
        client = new
        logger.info { "Switched clients for rsocket client ${config.name}" }

        logger.info { "Waiting for dispose..." }
        delay(config.pool.interval)
        old.dispose()
        logger.info { "Disposed old client for ${config.name}." }
    }
}
