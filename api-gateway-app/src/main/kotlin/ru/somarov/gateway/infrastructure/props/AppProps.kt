package ru.somarov.gateway.infrastructure.props

import io.ktor.server.application.*

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
                otel = OtelProps.parse(environment),
                clients = ClientsProps.parse(environment)
            )
        }
    }
}
