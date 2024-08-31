package ru.somarov.gateway.infrastructure.rsocket.server

data class Message<T: Any>(
    val body: T?,
    val metadata: Map<String, String>,
    val route: String
)
