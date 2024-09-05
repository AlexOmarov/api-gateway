package ru.somarov.gateway.infrastructure.config

import io.ktor.http.*
import io.ktor.serialization.kotlinx.cbor.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.rsocket.kotlin.ktor.server.RSocketSupport
import io.rsocket.transport.netty.client.TcpClientTransport
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import ru.somarov.gateway.application.service.Service
import ru.somarov.gateway.infrastructure.observability.setupObservability
import ru.somarov.gateway.infrastructure.props.AppProps
import ru.somarov.gateway.infrastructure.rsocket.client.Client
import ru.somarov.gateway.infrastructure.rsocket.server.Interceptor
import ru.somarov.gateway.infrastructure.rsocket.client.Config
import ru.somarov.gateway.presentation.http.healthcheck
import ru.somarov.gateway.presentation.rsocket.authSocket
import java.util.*
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("unused") // Referenced in application.yaml
@OptIn(ExperimentalSerializationApi::class)
internal fun Application.config() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val props = AppProps.parseProps(environment)

    val (meterRegistry, observationRegistry) = setupObservability(props)

    val service = Service()

    install(ContentNegotiation) { cbor(Cbor { ignoreUnknownKeys = true }) }

    install(MicrometerMetrics) {
        registry = meterRegistry
        meterBinders = listOf(JvmMemoryMetrics(), JvmGcMetrics(), ProcessorMetrics())
    }

    install(RequestValidation)

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause)
        }
    }

    install(WebSockets)

    install(RSocketSupport) {
        server {
            interceptors {
                forResponder(Interceptor(meterRegistry, observationRegistry))
            }
        }
    }

    val client = Client(
        Config(TcpClientTransport.create("", Random().nextInt()), meterRegistry, observationRegistry),
        EmptyCoroutineContext
    )

    routing {
        healthcheck()
        authSocket(service)
    }
}
