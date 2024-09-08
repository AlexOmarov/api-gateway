package ru.somarov.gateway.infrastructure.rsocket.client

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import reactor.util.retry.Retry
import reactor.util.retry.RetryBackoffSpec
import java.time.Duration

data class Config(
    val transport: String,
    val name: String,
    val meterRegistry: MeterRegistry,
    val observationRegistry: ObservationRegistry,
    val poolSize: Int = POOL_SIZE,
    val refreshInterval: Long = REFRESH_INTERVAL,
    val resumption: ResumptionConfig? = null,
    val reconnect: ReconnectConfig = ReconnectConfig(),
    val keepAlive: KeepAliveConfig = KeepAliveConfig(),
) {
    data class ReconnectConfig(
        val attempts: Long = RECONNECT_ATTEMPTS,
        val delay: Long = RECONNECT_DELAY,
        val jitter: Double = RECONNECT_JITTER
    ) {
        val retry: RetryBackoffSpec = Retry.fixedDelay(attempts, Duration.ofMillis(delay)).jitter(jitter)
    }

    data class ResumptionConfig(
        val attempts: Long = RESUME_ATTEMPTS,
        val delay: Long = RESUME_DELAY,
        val jitter: Double = RESUME_JITTER
    ) {
        val retry: RetryBackoffSpec = Retry.fixedDelay(attempts, Duration.ofMillis(delay)).jitter(jitter)
    }

    data class KeepAliveConfig(
        val interval: Long = KEEPALIVE_INTERVAL,
        val maxLifeTime: Long = KEEPALIVE_MAX_LIFETIME
    )

    companion object {
        private const val RECONNECT_ATTEMPTS = 5L
        private const val RECONNECT_DELAY = 1L
        private const val RECONNECT_JITTER = 1.0

        private const val RESUME_ATTEMPTS = 5L
        private const val RESUME_DELAY = 1L
        private const val RESUME_JITTER = 1.0

        private const val KEEPALIVE_INTERVAL = 1000L
        private const val KEEPALIVE_MAX_LIFETIME = 2000L

        private const val REFRESH_INTERVAL = 10_000L
        private const val POOL_SIZE = 3
    }
}
