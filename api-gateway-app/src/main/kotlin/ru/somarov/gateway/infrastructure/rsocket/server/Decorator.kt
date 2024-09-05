package ru.somarov.gateway.infrastructure.rsocket.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.metadata.WellKnownMimeType
import io.rsocket.micrometer.MicrometerRSocketInterceptor
import io.rsocket.micrometer.observation.ObservationResponderRSocketProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.reactor.awaitSingle
import reactor.core.publisher.Mono
import ru.somarov.gateway.infrastructure.observability.micrometer.observeSuspendedMono
import ru.somarov.gateway.infrastructure.rsocket.payload.deserialize
import ru.somarov.gateway.infrastructure.rsocket.payload.toJavaPayload
import ru.somarov.gateway.infrastructure.rsocket.payload.toKotlinPayload
import kotlin.coroutines.CoroutineContext

class Decorator(private val input: RSocket, private val registry: ObservationRegistry) : io.rsocket.RSocket {
    private val logger = logger { }
    private val mapper = ObjectMapper(CBORFactory()).registerKotlinModule()

    @ExperimentalMetadataApi
    override fun requestResponse(payload: io.rsocket.Payload): Mono<io.rsocket.Payload> {
        val context = (Dispatchers.IO + input.coroutineContext).minusKey(Job().key)
        val observation = registry.currentObservation!!

        return observation.observeSuspendedMono(coroutineContext = context) {
            val req = payload.deserialize(mapper)
            logger.info {
                "Incoming rsocket request -> ${req.metadata[WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.name]}: " +
                    "payload: ${req.body}, metadata: ${req.metadata}"
            }

            val result = input.requestResponse(payload.toKotlinPayload()).toJavaPayload()

            val resp = result.deserialize(mapper)
            logger.info {
                "Outgoing rsocket response <- ${resp.metadata[WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.name]}: " +
                    "payload: ${resp.body}, metadata: ${resp.metadata}"
            }

            result
        }.contextCapture()
    }

    companion object {
        @ExperimentalMetadataApi
        fun decorate(
            input: RSocket,
            observationRegistry: ObservationRegistry,
            meterRegistry: MeterRegistry
        ): RSocket {
            val enrichedJavaRSocket = ObservationResponderRSocketProxy(
                @Suppress
                MicrometerRSocketInterceptor(meterRegistry).apply(Decorator(input, observationRegistry)),
                observationRegistry
            )
            return object : RSocket {
                override val coroutineContext: CoroutineContext
                    get() = input.coroutineContext

                override suspend fun requestResponse(payload: Payload): Payload {
                    return enrichedJavaRSocket.requestResponse(payload.toJavaPayload())
                        .contextCapture()
                        .awaitSingle()
                        .toKotlinPayload()
                }
            }
        }
    }
}
