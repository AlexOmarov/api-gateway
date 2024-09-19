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
import ru.somarov.gateway.infrastructure.props.AppProps

object OpenTelemetrySdkFactory {
    fun create(props: AppProps): OpenTelemetrySdk {
        return OpenTelemetrySdk.builder()
            .setPropagators { W3CTraceContextPropagator.getInstance() }
            .setMeterProvider(createMeterProvider(props))
            .setLoggerProvider(createLoggerProvider(props))
            .setTracerProvider(createTracerProvider(props))
            .build()
    }

    private fun createMeterProvider(props: AppProps): SdkMeterProvider {
        val url = "${props.otel.protocol}://${props.otel.host}:${props.otel.metrics.port}"

        val exporter = OtlpGrpcMetricExporter.builder().setEndpoint(url).build()
        val reader = PeriodicMetricReader.builder(exporter).build()

        return SdkMeterProvider
            .builder()
            .registerMetricReader(reader)
            .setResource(createCommonResource(props))
            .build()
    }

    private fun createLoggerProvider(props: AppProps): SdkLoggerProvider {
        val url = "${props.otel.protocol}://${props.otel.host}:${props.otel.logs.port}"

        val exporter = OtlpGrpcLogRecordExporter.builder().setEndpoint(url).build()
        val processor = BatchLogRecordProcessor.builder(exporter).build()

        return SdkLoggerProvider
            .builder()
            .addLogRecordProcessor(processor)
            .setResource(createCommonResource(props))
            .build()
    }

    private fun createTracerProvider(props: AppProps): SdkTracerProvider {
        val url = "${props.otel.url}:${props.otel.traces.port}"

        val sampler = Sampler.parentBased(Sampler.traceIdRatioBased(props.otel.traces.probability))
        val exporter = OtlpGrpcSpanExporter.builder().setEndpoint(url).build()
        val processor = BatchSpanProcessor.builder(exporter).build()

        return SdkTracerProvider
            .builder()
            .setSampler(sampler)
            .addSpanProcessor(processor)
            .setResource(createCommonResource(props))
            .build()
    }

    private fun createCommonResource(props: AppProps) = Resource.create(
        Attributes.builder()
            .put("telemetry.sdk.name", "opentelemetry")
            .put("telemetry.sdk.language", "java")
            .put("telemetry.sdk.version", OtelVersion.VERSION)
            .put("service.name", props.name)
            .put("service.instance", props.instance)
            .build()
    )
}
