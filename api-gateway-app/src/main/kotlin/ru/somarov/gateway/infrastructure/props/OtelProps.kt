package ru.somarov.gateway.infrastructure.props

import io.ktor.server.application.*

data class OtelProps(
    val protocol: String,
    val host: String,
    val logsPort: Short,
    val metricsPort: Short,
    val tracingPort: Short,
    val tracingProbability: Double
) {
    companion object {
        fun parse(environment: ApplicationEnvironment): OtelProps {
            return OtelProps(
                protocol = environment.config.property("ktor.otel.protocol").getString(),
                host = environment.config.property("ktor.otel.host").getString(),
                logsPort = environment.config.property("ktor.otel.logs-port").getString().toShort(),
                metricsPort = environment.config.property("ktor.otel.metrics-port").getString().toShort(),
                tracingPort = environment.config.property("ktor.otel.tracing-port").getString().toShort(),
                tracingProbability = environment.config.property("ktor.otel.tracing-probability").getString()
                    .toDouble()
            )
        }
    }
}
