@file:Suppress("TooGenericExceptionCaught")

package ru.somarov.gateway.infrastructure.lib.observability.micrometer

import io.micrometer.core.instrument.kotlin.asContextElement
import io.micrometer.observation.Observation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import reactor.core.publisher.Mono
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

suspend fun <T> Observation.observeAndAwait(func: suspend () -> T): T {
    start()
    return try {
        withContext(openScope().use { observationRegistry.asContextElement() }) {
            func()
        }
    } catch (error: Throwable) {
        error(error)
        throw error
    } finally {
        stop()
    }
}

fun <T> Observation.observeSuspendedAsMono(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    runnable: suspend () -> T
): Mono<T> {
    start()
    return try {
        mono(coroutineContext + dispatcher + openScope().use { observationRegistry.asContextElement() }) {
            runnable()
        }
    } catch (error: Throwable) {
        error(error)
        throw error
    } finally {
        stop()
    }
}
