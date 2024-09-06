package ru.somarov.gateway.infrastructure.props

import io.ktor.server.application.*

data class ClientsProps(val auth: ClientProps) {
    data class ClientProps(
        val host: String,
        val port: Int
    )

    companion object {
        fun parse(environment: ApplicationEnvironment): ClientsProps {
            return ClientsProps(
                auth = ClientProps(
                    environment.config.property("ktor.clients.auth.host").getString(),
                    environment.config.property("ktor.clients.auth.port").getString().toInt()
                )
            )
        }
    }
}
