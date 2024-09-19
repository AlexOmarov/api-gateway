package ru.somarov.gateway.infrastructure.props

import io.ktor.server.application.*
import ru.somarov.gateway.infrastructure.lib.observability.OtelProps

data class AppProps(
    val name: String,
    val instance: String,
    val otel: OtelProps,
    val clients: ClientsProps,
) {
    companion object {
        fun parseProps(environment: ApplicationEnvironment): AppProps {
            return AppProps(
                name = environment.config.property("ktor.name").getString(),
                instance = environment.config.property("ktor.instance").getString(),
                otel = OtelProps.Companion.parse(environment),
                clients = ClientsProps.parse(environment)
            )
        }
    }
}
