package ru.somarov.gateway.infrastructure.lib.rsocket.client

import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readBytes
import io.rsocket.core.RSocketClient
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.util.ByteBufPayload
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.withContext
import reactor.core.publisher.Mono
import ru.somarov.gateway.infrastructure.lib.rsocket.payload.toJavaPayload
import ru.somarov.gateway.infrastructure.lib.rsocket.payload.toKotlinPayload
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalMetadataApi::class)
class Client(private val client: RSocketClient, override val coroutineContext: CoroutineContext) : RSocket {

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
}
