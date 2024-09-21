package ru.somarov.gateway.infrastructure.props

import io.ktor.server.application.*
import ru.somarov.gateway.infrastructure.lib.client.ClientProps
import ru.somarov.gateway.infrastructure.lib.observability.OtelProps

data class AppProps(
    val name: String,
    val instance: String,
    val buildPropsPath: String,
    val otel: OtelProps,
    val clients: ClientsProps,
) {
    data class ClientsProps(val auth: ClientProps)

    companion object {
        fun parseProps(environment: ApplicationEnvironment): AppProps {
            return AppProps(
                name = environment.config.property("ktor.name").getString(),
                buildPropsPath = environment.config.property("ktor.build-props-path").getString(),
                instance = environment.config.property("ktor.instance").getString(),
                otel = OtelProps.Companion.parse(environment),
                clients = ClientsProps(
                    auth = ClientProps.parse(environment, "ktor.clients.auth")
                )
            )
        }
    }
}
