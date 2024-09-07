package ru.somarov.gateway.application.service

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.utils.io.core.*
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.metadata.CompositeMetadata
import io.rsocket.kotlin.metadata.RoutingMetadata
import io.rsocket.kotlin.metadata.toPacket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.metadata.CompositeMetadataCodec
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import ru.somarov.gateway.infrastructure.rsocket.client.Client
import ru.somarov.gateway.presentation.request.RegistrationRequest
import ru.somarov.gateway.presentation.response.RegistrationResponse

class Service(private val client: Client) {
    private val log = logger { }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalMetadataApi::class)
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
}
