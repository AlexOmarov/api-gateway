package ru.somarov.gateway.infrastructure.rsocket.client

import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.metadata.WellKnownMimeType
import io.rsocket.plugins.RSocketInterceptor
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.encoding.Decoder
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import ru.somarov.gateway.infrastructure.rsocket.deserialize

class Interceptor: RSocketInterceptor {
    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun apply(rSocket: RSocket): RSocket {
        return object : RSocket {
            override fun requestResponse(payload: Payload): Mono<Payload> = proceed(rSocket, payload)
        }
    }

    private fun proceed(rSocket: RSocket, payload: Payload): Mono<Payload> {
        val req = payload.deserialize<Any>()
        log.info(
            "Outgoing RS request <- ${req.metadata[WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string]}: " +
                    "payload: ${req.body}, metadata: ${req.metadata}"
        )

        return rSocket.requestResponse(payload)
            .doOnSuccess {
                val resp = it.deserialize<Any>()
                log.info(
                    "Incoming RS response -> ${resp.metadata[WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string]}: " +
                            "payload: ${resp.body}, metadata: ${resp.metadata}"
                )
            }
    }
}
