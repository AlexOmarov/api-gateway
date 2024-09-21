package ru.somarov.gateway.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
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
import kotlinx.serialization.json.Json
import ru.somarov.gateway.application.service.Service
import ru.somarov.gateway.infrastructure.lib.observability.ObservabilityRegistryFactory
import ru.somarov.gateway.infrastructure.lib.observability.micrometer.observeAndAwait
import ru.somarov.gateway.infrastructure.lib.client.rsocket.Client
import ru.somarov.gateway.infrastructure.lib.client.rsocket.Config
import ru.somarov.gateway.infrastructure.props.AppProps
import ru.somarov.gateway.presentation.http.auth
import ru.somarov.gateway.presentation.http.healthcheck
import java.util.*
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("unused") // Referenced in application.yaml
internal fun Application.config() {
    val log = KotlinLogging.logger { }
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    val appProps = AppProps.parseProps(environment)
    val buildProps = parseBuildProperties("/META-INF/build-info.properties")

    val mapper = ObjectMapper(CBORFactory()).registerKotlinModule()

    val registry = ObservabilityRegistryFactory.create(appProps, buildProps)

    val client = Client(
        config = Config(
            host = appProps.clients.auth.host,
            name = "auth"
        ),
        registry = registry,
        mapper = mapper,
        coroutineContext = EmptyCoroutineContext
    )

    val service = Service(client)

    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }

    install(MicrometerMetrics) {
        this.registry = registry.meterRegistry
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
        val context = ReceiverContext<ApplicationRequest> { request, key -> request.headers[key] }
        context.carrier = this.call.request
        Observation.createNotStarted("http_observation", { context }, registry.observationRegistry)
            .observeAndAwait { proceed() }
    }

    routing {
        healthcheck()
        auth(service)
    }
}

private fun parseBuildProperties(path: String) =
    Properties().also { props -> Application::class.java.getResourceAsStream(path)?.use { props.load(it) } }
