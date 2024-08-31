package ru.somarov.gateway.presentation.http

import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

internal fun Routing.healthcheck() {
    get("health") {
        call.respondText("UP")
    }
}
