package ru.somarov.gateway.presentation.rsocket

import io.ktor.server.routing.*
import io.ktor.utils.io.core.*
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.ktor.server.rSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import ru.somarov.gateway.application.service.Service

internal fun Routing.authSocket(service: Service) {
    rSocket("validate") {
        RSocketRequestHandler {
            requestResponse { validate(it, service) }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
private suspend fun validate(payload: Payload, service: Service): Payload {
    val req = Cbor.Default.decodeFromByteArray<String>(payload.data.readBytes())
    return buildPayload {
        data { writePacket(ByteReadPacket(Cbor.Default.encodeToByteArray("114"))) }
    }
}
