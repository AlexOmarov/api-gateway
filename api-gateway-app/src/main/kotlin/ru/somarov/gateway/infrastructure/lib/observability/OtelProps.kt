package ru.somarov.gateway.infrastructure.lib.observability

import io.ktor.server.application.*

data class OtelProps(
    val protocol: String,
    val host: String,
    val logs: LogsProps,
    val metrics: MetricsProps,
    val traces: TracesProps
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
        fun parse(environment: ApplicationEnvironment, prefix: String = "ktor.otel") = OtelProps(
            protocol = environment.config.property("$prefix.protocol").getString(),
            host = environment.config.property("$prefix.host").getString(),
            logs = LogsProps(environment.config.property("$prefix.logs.port").getString().toShort()),
            metrics = MetricsProps(environment.config.property("$prefix.metrics.port").getString().toShort()),
            traces = TracesProps(
                environment.config.property("$prefix.tracing.port").getString().toShort(),
                environment.config.property("$prefix.tracing.probability").getString().toDouble()
            )
        )
    }
}
