package ru.somarov.gateway.infrastructure.rsocket.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.metadata.WellKnownMimeType
import io.rsocket.plugins.RSocketInterceptor
import reactor.core.publisher.Mono
import ru.somarov.gateway.infrastructure.rsocket.payload.deserialize

class Interceptor : RSocketInterceptor {
    private val log = logger { }
    private val mapper = ObjectMapper(CBORFactory()).registerKotlinModule()

    override fun apply(rSocket: RSocket): RSocket {
        return object : RSocket {
            override fun requestResponse(payload: Payload): Mono<Payload> = proceed(rSocket, payload)
        }
    }

    private fun proceed(rSocket: RSocket, payload: Payload): Mono<Payload> {
        val req = payload.deserialize(mapper)

        log.info {
            "Outgoing RS request <- ${req.metadata[WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string]}: " +
                "payload: ${req.body}, metadata: ${req.metadata}"
        }

        return rSocket.requestResponse(payload)
            .doOnSuccess {
                val resp = it.deserialize(mapper)
                log.info {
                    "Incoming RS response -> ${req.metadata[WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string]}: " +
                        "payload: ${resp.body}, metadata: ${resp.metadata}"
                }
            }
    }
}
