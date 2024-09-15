package ru.somarov.gateway.infrastructure.rsocket.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.metadata.WellKnownMimeType
import io.rsocket.plugins.RSocketInterceptor
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.somarov.gateway.infrastructure.rsocket.payload.deserialize

class LoggingInterceptor(private val mapper: ObjectMapper) : RSocketInterceptor {
    private val log = logger { }

    override fun apply(rSocket: RSocket): RSocket {
        return createDecorator(rSocket)
    }

    private fun createDecorator(input: RSocket) = object : RSocket {
        override fun requestResponse(payload: Payload): Mono<Payload> {
            log.info { createMessage("Outgoing RS request <-", payload) }

            return input
                .requestResponse(payload)
                .doOnSuccess { log.info { createMessage("Incoming RS response ->", payload) } }
        }

        override fun fireAndForget(payload: Payload): Mono<Void> {
            log.info { createMessage("Outgoing RS fire <-", payload) }

            return input
                .fireAndForget(payload)
                .doOnSuccess { log.info { "Completed" } }
        }

        override fun requestStream(payload: Payload): Flux<Payload> {
            log.info { createMessage("Outgoing RS stream payload <-", payload) }

            return input
                .requestStream(payload)
                .doOnNext { log.info { createMessage("Incoming RS stream payload ->", payload) } }
        }

        override fun requestChannel(payloads: Publisher<Payload>): Flux<Payload> {
            val loggedPayloads = payloads
                .asFlow()
                .onEach { log.info { createMessage("Outgoing RS channel payload <-", it) } }
                .asPublisher()

            return input
                .requestChannel(loggedPayloads)
                .doOnNext { log.info { createMessage("Incoming RS channel payload ->", it) } }
        }
    }

    private fun createMessage(type: String, payload: Payload): String {
        val parsed = payload.deserialize(mapper)
        return "$type " +
            "${parsed.metadata[WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string]}: " +
            "payload: ${parsed.body}, " +
            "metadata: ${parsed.metadata}"
    }
}
