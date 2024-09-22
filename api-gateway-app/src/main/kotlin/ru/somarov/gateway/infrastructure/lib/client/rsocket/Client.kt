package ru.somarov.gateway.infrastructure.lib.client.rsocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readBytes
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.metadata.WellKnownMimeType
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
    registry: ObservabilityRegistry,
    private val mapper: ObjectMapper,
    override val coroutineContext: CoroutineContext
) : RSocket {
    private val logger = logger { }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var client = RSocketClientFactory.create(config, registry)

    init {
        scope.launch {
            delay(config.pool.interval)
            while (true) {
                refreshPool(config, registry)
            }
        }
    }

    override suspend fun requestResponse(payload: Payload): Payload {
        val convertedPayload = payload.toJavaPayload()
        logger.info { createMessage("Outgoing RS request <-", convertedPayload) }
        return withContext(coroutineContext) {
            client.requestResponse(Mono.just(convertedPayload))
                .contextCapture()
                .contextWrite { it.putAllMap(foldContext()) }
                .doOnNext { logger.info { createMessage("Incoming RS response ->", it) } }
                .awaitSingle()
                .toKotlinPayload()
        }
    }

    override fun requestStream(payload: Payload): Flow<Payload> {
        val convertedPayload = payload.toJavaPayload()
        logger.info { createMessage("Outgoing RS stream payload <-", convertedPayload) }
        return client.requestStream(Mono.just(convertedPayload))
            .contextCapture()
            .contextWrite { it.putAllMap(foldContext()) }
            .doOnNext { logger.info { createMessage("Incoming RS stream payload ->", it) } }
            .asFlow()
            .map { it.toKotlinPayload() }
    }

    override suspend fun fireAndForget(payload: Payload) {
        val convertedPayload = payload.toJavaPayload()
        logger.info { createMessage("Outgoing RS fire <-", convertedPayload) }
        return withContext(coroutineContext) {
            client.fireAndForget(Mono.just(convertedPayload))
                .contextCapture()
                .contextWrite { it.putAllMap(foldContext()) }
                .doOnSuccess { logger.info { "Outgoing RS fire completed" } }
                .awaitSingleOrNull()
        }
    }

    override fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> {
        val unifiedFlux = flowOf(initPayload)
            .onCompletion { if (it == null) emitAll(payloads.map { it }) }
            .map { it.toJavaPayload() }
            .onEach { logger.info { createMessage("Outgoing RS channel payload <-", it) } }
            .asFlux()

        return client.requestChannel(unifiedFlux)
            .contextCapture()
            .contextWrite { it.putAllMap(foldContext()) }
            .doOnNext { logger.info { createMessage("Incoming RS channel payload ->", it) } }
            .asFlow()
            .map { it.toKotlinPayload() }
    }

    override suspend fun metadataPush(metadata: ByteReadPacket) {
        val payload = ByteBufPayload.create(ByteArray(0), metadata.readBytes())
        logger.info { createMessage("Outgoing RS metadata <-", payload) }
        client.metadataPush(Mono.just(payload))
            .contextCapture()
            .contextWrite { it.putAllMap(foldContext()) }
            .doOnSuccess { logger.info { "Outgoing RS metadata completed" } }
            .awaitSingleOrNull()
    }

    private fun foldContext() = coroutineContext
        .fold(mutableMapOf<String, Any?>()) { acc, el -> acc.also { it[el.key.toString()] = el } }

    private suspend fun refreshPool(config: Config, registry: ObservabilityRegistry) {
        logger.info { "RSocket ${config.name} update iteration launched" }
        val new = RSocketClientFactory.create(config, registry)
        val old = client
        client = new
        logger.info { "Switched clients for rsocket client ${config.name}" }

        logger.info { "Waiting for dispose..." }
        delay(config.pool.interval)
        old.dispose()
        logger.info { "Disposed old client for ${config.name}." }
    }

    private fun createMessage(type: String, payload: io.rsocket.Payload): String {
        val parsed = payload.deserialize(mapper)
        return "$type " +
            "${parsed.metadata[WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string]}: " +
            "payload: ${parsed.body}, " +
            "metadata: ${parsed.metadata}"
    }
}
