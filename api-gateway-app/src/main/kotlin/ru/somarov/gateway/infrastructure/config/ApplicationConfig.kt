package ru.somarov.gateway.infrastructure.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.observation.Observation
import io.micrometer.observation.transport.ReceiverContext
import io.rsocket.transport.netty.client.TcpClientTransport
import kotlinx.serialization.json.Json
import ru.somarov.gateway.application.service.Service
import ru.somarov.gateway.infrastructure.observability.micrometer.observeAndAwait
import ru.somarov.gateway.infrastructure.observability.setupObservability
import ru.somarov.gateway.infrastructure.props.AppProps
import ru.somarov.gateway.infrastructure.rsocket.client.Client
import ru.somarov.gateway.infrastructure.rsocket.client.Config
import ru.somarov.gateway.presentation.http.auth
import ru.somarov.gateway.presentation.http.healthcheck
import java.util.*
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("unused") // Referenced in application.yaml
internal fun Application.config() {
    val log = KotlinLogging.logger { }
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val props = AppProps.parseProps(environment)

    val (meterRegistry, observationRegistry) = setupObservability(props)

    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }

    install(MicrometerMetrics) {
        registry = meterRegistry
        meterBinders = listOf(JvmMemoryMetrics(), JvmGcMetrics(), ProcessorMetrics())
    }

    install(RequestValidation)

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error(cause) { "Got exception while processing call" }
            call.respond(HttpStatusCode.BadRequest, "Got error ${cause.message}")
        }
    }

    intercept(ApplicationCallPipeline.Monitoring) {
        val observation = Observation.createNotStarted(
            "http_observation",
            {
                ReceiverContext<ApplicationRequest> { request, key -> request.headers[key] }
                    .also { it.carrier = this.call.request }
            },
            observationRegistry
        )

        observation.observeAndAwait {
            proceed()
        }
    }

    val client = Client(
        Config(
            TcpClientTransport.create(props.clients.auth.host, props.clients.auth.port),
            meterRegistry,
            observationRegistry
        ),
        EmptyCoroutineContext
    )

    val service = Service(client)
    // TODO: why request keeps going, redo dispose client use count down latch

    routing {
        healthcheck()
        auth(service)
    }
}
