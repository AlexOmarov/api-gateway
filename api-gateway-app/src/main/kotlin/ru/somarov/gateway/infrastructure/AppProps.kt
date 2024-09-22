package ru.somarov.gateway.infrastructure

import io.ktor.server.application.*
import ru.somarov.gateway.infrastructure.lib.client.ClientProps
import ru.somarov.gateway.infrastructure.lib.observability.props.OtelProps

data class AppProps(
    val monitoring: OtelProps,
    val clients: ClientsProps,
) {
    data class ClientsProps(val auth: ClientProps)

    companion object {
        fun parse(environment: ApplicationEnvironment): AppProps {
            return AppProps(
                monitoring = OtelProps.parse(environment),
                clients = ClientsProps(auth = ClientProps.parse(environment, "ktor.clients.auth"))
            )
        }
    }
}
