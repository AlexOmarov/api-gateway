package ru.somarov.gateway.infrastructure.observability

import io.ktor.server.application.Application
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import io.micrometer.tracing.handler.DefaultTracingObservationHandler
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler
import io.micrometer.tracing.handler.TracingAwareMeterObservationHandler
import io.micrometer.tracing.otel.bridge.EventPublishingContextWrapper
import io.micrometer.tracing.otel.bridge.OtelBaggageManager
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext
import io.micrometer.tracing.otel.bridge.OtelPropagator
import io.micrometer.tracing.otel.bridge.OtelTracer
import io.micrometer.tracing.otel.bridge.Slf4JBaggageEventListener
import io.micrometer.tracing.otel.bridge.Slf4JEventListener
import io.opentelemetry.context.ContextStorage
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry
import io.rsocket.micrometer.observation.ByteBufGetter
import io.rsocket.micrometer.observation.ByteBufSetter
import io.rsocket.micrometer.observation.RSocketRequesterTracingObservationHandler
import io.rsocket.micrometer.observation.RSocketResponderTracingObservationHandler
import ru.somarov.gateway.infrastructure.observability.opentelemetry.createOpenTelemetrySdk
import ru.somarov.gateway.infrastructure.props.AppProps
import java.util.Collections
import java.util.Properties

fun setupObservability(props: AppProps): Pair<MeterRegistry, ObservationRegistry> {
    val sdk = createOpenTelemetrySdk(props)
    val buildProps = getBuildProperties()

    val meterRegistry = OpenTelemetryMeterRegistry.create(sdk).also {
        it.config().commonTags(
            "application", props.name,
            "instance", props.instance
        )
    }

    Gauge.builder("project_version") { 1 }
        .description("Version of project in tag")
        .tag("version", buildProps.getProperty("version", "undefined"))
        .register(meterRegistry)

    val oteltracer = sdk.tracerProvider["io.micrometer.micrometer-tracing"]
    val context = OtelCurrentTraceContext()
    val listener = Slf4JEventListener()
    val baggageListener = Slf4JBaggageEventListener(Collections.emptyList())
    val publisher = { it: Any -> listener.onEvent(it); baggageListener.onEvent(it) }
    val tracer = OtelTracer(
        oteltracer, context, publisher,
        OtelBaggageManager(context, Collections.emptyList(), Collections.emptyList())
    )
    val propagator = OtelPropagator(sdk.propagators, oteltracer)

    val observationRegistry = ObservationRegistry.create().also {
        it.observationConfig()
            .observationHandler(
                ObservationHandler.FirstMatchingCompositeObservationHandler(
                    RSocketRequesterTracingObservationHandler(tracer, propagator, ByteBufSetter(), false),
                    // RSocketResponderTracingObservationHandler onstart cleans traceparent for some reason
                    RSocketResponderTracingObservationHandler(tracer, propagator, ByteBufGetter(), false),
                    PropagatingReceiverTracingObservationHandler(tracer, propagator),
                    PropagatingSenderTracingObservationHandler(tracer, propagator),
                    DefaultTracingObservationHandler(tracer)
                )
            )
            .observationHandler(
                TracingAwareMeterObservationHandler(
                    DefaultMeterObservationHandler(meterRegistry),
                    tracer
                )
            )
    }

    ContextStorage.addWrapper(EventPublishingContextWrapper(publisher))
    OpenTelemetryAppender.install(sdk)

    return Pair(meterRegistry, observationRegistry)
}

private fun getBuildProperties(): Properties {
    val properties = Properties()
    Application::class.java.getResourceAsStream("/META-INF/build-info.properties")?.use {
        properties.load(it)
    }
    return properties
}
