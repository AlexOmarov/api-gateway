package ru.somarov.gateway.infrastructure.props

import io.ktor.server.application.*

data class AppProps(
    val name: String,
    val instance: String,
    val otel: OtelProps,
) {

    data class OtelProps(
        val protocol: String,
        val host: String,
        val logsPort: Short,
        val metricsPort: Short,
        val tracingPort: Short,
        val tracingProbability: Double
    )

    companion object {
        fun parseProps(environment: ApplicationEnvironment): AppProps {
            return AppProps(
                name = environment.config.property("ktor.name").getString(),
                instance = environment.config.property("ktor.instance").getString(),
                otel = OtelProps(
                    protocol = environment.config.property("ktor.otel.protocol").getString(),
                    host = environment.config.property("ktor.otel.host").getString(),
                    logsPort = environment.config.property("ktor.otel.logs-port").getString().toShort(),
                    metricsPort = environment.config.property("ktor.otel.metrics-port").getString().toShort(),
                    tracingPort = environment.config.property("ktor.otel.tracing-port").getString().toShort(),
                    tracingProbability = environment.config.property("ktor.otel.tracing-probability").getString()
                        .toDouble()
                )
            )
        }
    }
}
