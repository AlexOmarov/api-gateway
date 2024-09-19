package ru.somarov.gateway.application.service

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.utils.io.core.*
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.metadata.CompositeMetadata
import io.rsocket.kotlin.metadata.RoutingMetadata
import io.rsocket.kotlin.metadata.toPacket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.metadata.CompositeMetadataCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import ru.somarov.gateway.presentation.request.RegistrationRequest
import ru.somarov.gateway.presentation.response.RegistrationResponse
import java.util.UUID

@OptIn(ExperimentalSerializationApi::class, ExperimentalMetadataApi::class)
class Service(private val client: RSocket) {
    private val log = logger { }

    suspend fun register(request: RegistrationRequest): RegistrationResponse {
        log.info { "Incoming request $request" }
        val metadata = ByteBufAllocator.DEFAULT.compositeBuffer()
        CompositeMetadata(RoutingMetadata("register")).entries.forEach {
            CompositeMetadataCodec.encodeAndAddMetadata(
                /* compositeMetaData = */ metadata,
                /* allocator = */ ByteBufAllocator.DEFAULT,
                /* customMimeType = */ it.mimeType.toString(),
                /* metadata = */ Unpooled.wrappedBuffer(it.content.readBytes())
            )
        }

        return Cbor.Default.decodeFromByteArray<RegistrationResponse>(
            client.requestResponse(
                Payload(
                    buildPacket { writeFully(Cbor.Default.encodeToByteArray(request)) },
                    CompositeMetadata(RoutingMetadata("register")).toPacket(),
                )
            ).data.readBytes()
        )
    }

    fun stream(request: RegistrationRequest): Flow<RegistrationResponse> {
        log.info { "Incoming request for stream $request" }

        val metadata = ByteBufAllocator.DEFAULT.compositeBuffer()
        CompositeMetadata(RoutingMetadata("stream")).entries.forEach {
            CompositeMetadataCodec.encodeAndAddMetadata(
                /* compositeMetaData = */ metadata,
                /* allocator = */ ByteBufAllocator.DEFAULT,
                /* customMimeType = */ it.mimeType.toString(),
                /* metadata = */ Unpooled.wrappedBuffer(it.content.readBytes())
            )
        }

        val streamId = UUID.randomUUID()

        val payload = Payload(
            buildPacket { writeFully(Cbor.Default.encodeToByteArray(request)) },
            CompositeMetadata(RoutingMetadata("stream")).toPacket()
        )

        return client.requestStream(payload).map {
            val parsed = Cbor.Default.decodeFromByteArray<RegistrationResponse>(it.data.readBytes())
            log.info { "Got $parsed as response for stream $streamId" }
            parsed
        }.onCompletion { log.info { "COMPLETED!!!" } }
    }
}
