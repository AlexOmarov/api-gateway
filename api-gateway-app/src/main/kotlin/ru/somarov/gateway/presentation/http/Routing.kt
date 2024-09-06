package ru.somarov.gateway.presentation.http

import io.ktor.server.application.call
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.somarov.gateway.application.service.Service
import ru.somarov.gateway.presentation.request.RegistrationRequest

internal fun Routing.healthcheck() {
    get("health") {
        call.respondText("UP")
    }
}

internal fun Routing.auth(service: Service) {
    post<RegistrationRequest>("register") {
        service.register(it)
        call.respond(service.register(it))
    }
}
