package ru.somarov.gateway.infrastructure.rsocket.server

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.Interceptor

internal class Interceptor(
    private val mapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
    private val observationRegistry: ObservationRegistry
) : Interceptor<RSocket> {

    override fun intercept(input: RSocket): RSocket {
        return Decorator.decorate(input, mapper, observationRegistry, meterRegistry)
    }
}
