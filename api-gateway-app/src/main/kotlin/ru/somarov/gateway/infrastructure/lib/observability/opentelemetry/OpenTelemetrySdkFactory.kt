package ru.somarov.gateway.infrastructure.lib.observability.opentelemetry

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.internal.OtelVersion
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.samplers.Sampler
import ru.somarov.gateway.infrastructure.lib.observability.props.OtelProps

object OpenTelemetrySdkFactory {
    fun create(props: OtelProps): OpenTelemetrySdk {
        return OpenTelemetrySdk.builder()
            .setPropagators { W3CTraceContextPropagator.getInstance() }
            .setMeterProvider(createMeterProvider(props))
            .setLoggerProvider(createLoggerProvider(props))
            .setTracerProvider(createTracerProvider(props))
            .build()
    }

    private fun createMeterProvider(props: OtelProps): SdkMeterProvider {
        val url = "${props.protocol}://${props.host}:${props.metrics.port}"

        val exporter = OtlpGrpcMetricExporter.builder().setEndpoint(url).build()
        val reader = PeriodicMetricReader.builder(exporter).build()

        return SdkMeterProvider
            .builder()
            .registerMetricReader(reader)
            .setResource(createCommonResource(props))
            .build()
    }

    private fun createLoggerProvider(props: OtelProps): SdkLoggerProvider {
        val url = "${props.protocol}://${props.host}:${props.logs.port}"

        val exporter = OtlpGrpcLogRecordExporter.builder().setEndpoint(url).build()
        val processor = BatchLogRecordProcessor.builder(exporter).build()

        return SdkLoggerProvider
            .builder()
            .addLogRecordProcessor(processor)
            .setResource(createCommonResource(props))
            .build()
    }

    private fun createTracerProvider(props: OtelProps): SdkTracerProvider {
        val url = "${props.url}:${props.traces.port}"

        val sampler = Sampler.parentBased(Sampler.traceIdRatioBased(props.traces.probability))
        val exporter = OtlpGrpcSpanExporter.builder().setEndpoint(url).build()
        val processor = BatchSpanProcessor.builder(exporter).build()

        return SdkTracerProvider
            .builder()
            .setSampler(sampler)
            .addSpanProcessor(processor)
            .setResource(createCommonResource(props))
            .build()
    }

    private fun createCommonResource(props: OtelProps) = Resource.create(
        Attributes.builder()
            .put("telemetry.sdk.name", "opentelemetry")
            .put("telemetry.sdk.language", "java")
            .put("telemetry.sdk.version", OtelVersion.VERSION)
            .put("service.name", props.name)
            .put("service.instance", props.instance)
            .build()
    )
}
