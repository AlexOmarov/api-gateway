package ru.somarov.gateway.infrastructure.props

import io.ktor.server.application.*
import ru.somarov.gateway.infrastructure.build.BuildProps
import ru.somarov.gateway.infrastructure.lib.client.ClientProps
import ru.somarov.gateway.infrastructure.lib.observability.OtelProps

data class AppProps(
    val name: String,
    val instance: String,
    val otel: OtelProps,
    val clients: ClientsProps,
    val build: BuildProps,
) {
    data class ClientsProps(val auth: ClientProps)

    companion object {
        fun parse(environment: ApplicationEnvironment): AppProps {
            return AppProps(
                name = environment.config.property("ktor.name").getString(),
                instance = environment.config.property("ktor.instance").getString(),
                otel = OtelProps.parse(environment),
                clients = ClientsProps(auth = ClientProps.parse(environment, "ktor.clients.auth")),
                build = BuildProps.parse(environment.config.property("ktor.build-props-path").getString())
            )
        }
    }
}
