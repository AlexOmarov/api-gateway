package ru.somarov.gateway.infrastructure.lib.observability

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
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.ContextStorage
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.rsocket.micrometer.observation.ByteBufGetter
import io.rsocket.micrometer.observation.ByteBufSetter
import io.rsocket.micrometer.observation.RSocketRequesterTracingObservationHandler
import io.rsocket.micrometer.observation.RSocketResponderTracingObservationHandler
import ru.somarov.gateway.infrastructure.lib.observability.opentelemetry.OpenTelemetrySdkFactory
import ru.somarov.gateway.infrastructure.props.AppProps
import java.util.Collections
import java.util.Properties

object ObservabilityRegistriesFactory {
    fun create(props: AppProps, buildProps: Properties): Pair<MeterRegistry, ObservationRegistry> {
        val sdk = OpenTelemetrySdkFactory.create(props)

        val meterRegistry = createMeterRegistry(sdk, props, buildProps)
        val observationRegistry = createObservationRegistry(sdk, meterRegistry)

        OpenTelemetryAppender.install(sdk)

        return Pair(meterRegistry, observationRegistry)
    }

    private fun createObservationRegistry(sdk: OpenTelemetrySdk, meterRegistry: MeterRegistry): ObservationRegistry {
        val tracer = sdk.tracerProvider["io.micrometer.micrometer-tracing"]

        val propagator = OtelPropagator(sdk.propagators, tracer)
        val tracerWrapper = wrapTracer(tracer)

        val registry = ObservationRegistry.create()

        val propagationHandler = ObservationHandler.FirstMatchingCompositeObservationHandler(
            RSocketRequesterTracingObservationHandler(tracerWrapper, propagator, ByteBufSetter(), false),
            // RSocketResponderTracingObservationHandler onstart cleans traceparent for some reason
            RSocketResponderTracingObservationHandler(tracerWrapper, propagator, ByteBufGetter(), false),
            PropagatingReceiverTracingObservationHandler(tracerWrapper, propagator),
            PropagatingSenderTracingObservationHandler(tracerWrapper, propagator),
            DefaultTracingObservationHandler(tracerWrapper)
        )

        val meterHandler = TracingAwareMeterObservationHandler(
            DefaultMeterObservationHandler(meterRegistry),
            tracerWrapper
        )

        registry.observationConfig()
            .observationHandler(propagationHandler)
            .observationHandler(meterHandler)

        return registry
    }

    private fun wrapTracer(tracer: Tracer): OtelTracer {
        val context = OtelCurrentTraceContext()
        val listener = Slf4JEventListener()
        val baggageListener = Slf4JBaggageEventListener(Collections.emptyList())
        val publisher = { it: Any -> listener.onEvent(it); baggageListener.onEvent(it) }
        ContextStorage.addWrapper(EventPublishingContextWrapper(publisher))

        val baggage = OtelBaggageManager(context, Collections.emptyList(), Collections.emptyList())

        return OtelTracer(tracer, context, publisher, baggage)
    }

    private fun createMeterRegistry(sdk: OpenTelemetrySdk, props: AppProps, buildProps: Properties): MeterRegistry {
        val registry = OpenTelemetryMeterRegistry.create(sdk)

        registry.config().commonTags(
            "application", props.name,
            "instance", props.instance
        )

        Gauge.builder("project_version") { 1 }
            .description("Version of project in tag")
            .tag("version", buildProps.getProperty("version", "undefined"))
            .register(registry)

        return registry
    }
}
