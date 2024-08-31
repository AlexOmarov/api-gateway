package ru.somarov.gateway.infrastructure.rsocket.client

import io.rsocket.transport.ClientTransport
import reactor.util.retry.Retry
import reactor.util.retry.RetryBackoffSpec
import java.time.Duration

data class Config(
    val transport: ClientTransport,
    val resumption: ResumptionConfig = ResumptionConfig(),
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

        private const val KEEPALIVE_INTERVAL = 3L
        private const val KEEPALIVE_MAX_LIFETIME = 8L
    }
}
