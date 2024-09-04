package ru.somarov.gateway.infrastructure.rsocket.server

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.Interceptor

internal class Interceptor(
    private val meterRegistry: MeterRegistry,
    private val observationRegistry: ObservationRegistry
) : Interceptor<RSocket> {

    @ExperimentalMetadataApi
    override fun intercept(input: RSocket): RSocket {
        return Decorator.decorate(input, observationRegistry, meterRegistry)
    }
}
