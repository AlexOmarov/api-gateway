package ru.somarov.gateway.infrastructure.rsocket.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.plugins.RSocketInterceptor
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import ru.somarov.gateway.infrastructure.rsocket.deserialize

class Interceptor(private val mapper : ObjectMapper) : RSocketInterceptor {
    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun apply(rSocket: RSocket): RSocket {
        return object : RSocket {
            override fun requestResponse(payload: Payload): Mono<Payload> = proceed(rSocket, payload)
        }
    }

    private fun proceed(rSocket: RSocket, payload: Payload): Mono<Payload> {
        val req = payload.deserialize(mapper, log, Any::class.java)
        log.info("Outgoing rsocket request <- ${req.route}: payload: ${req.body}, metadata: ${req.metadata}")

        return rSocket.requestResponse(payload)
            .doOnSuccess {
                val resp = it.deserialize(mapper, log, Any::class.java)
                log.info(
                    "Incoming rsocket response -> ${resp.route}: " +
                            "payload: ${resp.body}, " +
                            "metadata: ${resp.metadata}"
                )
            }
    }
}
