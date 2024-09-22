package ru.somarov.gateway.infrastructure.lib.observability.props

import io.ktor.server.application.*

data class OtelProps(
    val name: String,
    val instance: String,
    val protocol: String,
    val host: String,
    val logs: LogsProps,
    val metrics: MetricsProps,
    val traces: TracesProps,
    val build: BuildProps
) {
    val url = "$protocol://$host"

    data class LogsProps(
        val port: Short
    )

    data class MetricsProps(
        val port: Short
    )

    data class TracesProps(
        val port: Short,
        val probability: Double
    )

    companion object {
        fun parse(environment: ApplicationEnvironment) = OtelProps(
            name = environment.config.property("ktor.monitoring.name").getString(),
            instance = environment.config.property("ktor.monitoring.instance").getString(),
            protocol = environment.config.property("ktor.monitoring.protocol").getString(),
            host = environment.config.property("ktor.monitoring.host").getString(),
            logs = LogsProps(environment.config.property("ktor.monitoring.logs.port").getString().toShort()),
            metrics = MetricsProps(environment.config.property("ktor.monitoring.metrics.port").getString().toShort()),
            traces = TracesProps(
                environment.config.property("ktor.monitoring.tracing.port").getString().toShort(),
                environment.config.property("ktor.monitoring.tracing.probability").getString().toDouble()
            ),
            build = BuildProps.parse(environment.config.property("ktor.monitoring.build-props-path").getString())
        )
    }
}
