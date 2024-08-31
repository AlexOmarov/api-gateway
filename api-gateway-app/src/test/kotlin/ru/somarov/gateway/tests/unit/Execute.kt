package ru.somarov.gateway.tests.unit

import io.ktor.server.config.*
import io.ktor.server.testing.*

fun execute(func: suspend (ApplicationTestBuilder) -> Unit) {
    testApplication {
        environment { config = config.mergeWith(ApplicationConfig("application.yaml")) }
        func(this)
    }
}
