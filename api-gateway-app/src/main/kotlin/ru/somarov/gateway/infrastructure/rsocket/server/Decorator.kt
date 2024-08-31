package ru.somarov.gateway.infrastructure.rsocket.server

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.micrometer.MicrometerRSocketInterceptor
import io.rsocket.micrometer.observation.ObservationResponderRSocketProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory.getLogger
import reactor.core.publisher.Mono
import ru.somarov.gateway.infrastructure.observability.micrometer.observeSuspendedMono
import ru.somarov.gateway.infrastructure.rsocket.deserialize
import ru.somarov.gateway.infrastructure.rsocket.from
import ru.somarov.gateway.infrastructure.rsocket.toKotlinPayload
import kotlin.coroutines.CoroutineContext

class Decorator(
    private val input: RSocket,
    private val mapper: ObjectMapper,
    private val registry: ObservationRegistry
) : io.rsocket.RSocket {
    private val logger = getLogger(this.javaClass)

    @OptIn(ExperimentalMetadataApi::class)
    override fun requestResponse(payload: io.rsocket.Payload): Mono<io.rsocket.Payload> {
        val context = (Dispatchers.IO + input.coroutineContext).minusKey(Job().key)
        val observation = registry.currentObservation!!

        return observation.observeSuspendedMono(coroutineContext = context) {
            val req = payload.deserialize(mapper, logger, Any::class.java)
            logger.info("Incoming rsocket request -> ${req.route}: payload: ${req.body}, metadata: ${req.metadata}")

            val result = io.rsocket.Payload::class.from(input.requestResponse(payload.toKotlinPayload()))

            val resp = result.deserialize(mapper, logger, Any::class.java)
            logger.info("Outgoing rsocket response <- ${resp.route}: payload: ${resp.body}, metadata: ${resp.metadata}")

            result
        }.contextCapture()
    }


    companion object {
        @OptIn(ExperimentalMetadataApi::class)
        fun decorate(
            input: RSocket,
            mapper: ObjectMapper,
            observationRegistry: ObservationRegistry,
            meterRegistry: MeterRegistry
        ): RSocket {
            val enrichedJavaRSocket = ObservationResponderRSocketProxy(
                MicrometerRSocketInterceptor(meterRegistry).apply(Decorator(input, mapper, observationRegistry)),
                observationRegistry
            )
            return object : RSocket {
                override val coroutineContext: CoroutineContext
                    get() = input.coroutineContext

                override suspend fun requestResponse(payload: Payload): Payload {
                    return enrichedJavaRSocket.requestResponse(io.rsocket.Payload::class.from(payload))
                        .contextCapture()
                        .awaitSingle()
                        .toKotlinPayload()
                }
            }
        }
    }
}