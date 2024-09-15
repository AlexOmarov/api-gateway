package ru.somarov.gateway.presentation.http

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.somarov.gateway.application.service.Service
import ru.somarov.gateway.presentation.request.RegistrationRequest

internal fun Routing.healthcheck() {
    get("health") {
        call.respondText("UP")
    }
}

internal fun Routing.auth(service: Service) {
    post<RegistrationRequest>("register") {
        call.respond(service.register(it))
    }

    post<RegistrationRequest>("stream") { request ->
        call.respondOutputStream(ContentType.Application.Json) {
            service.stream(request)
                .flowOn(Dispatchers.IO)
                .collect { response ->
                    val jsonResponse = Json.encodeToString(response)
                    write(jsonResponse.toByteArray())
                    flush() // Send each JSON object immediately
                }
        }
    }
}
