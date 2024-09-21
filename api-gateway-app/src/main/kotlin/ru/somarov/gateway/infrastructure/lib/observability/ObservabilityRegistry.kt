package ru.somarov.gateway.infrastructure.lib.observability

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry

data class ObservabilityRegistry(
    val meterRegistry: MeterRegistry,
    val observationRegistry: ObservationRegistry
)
