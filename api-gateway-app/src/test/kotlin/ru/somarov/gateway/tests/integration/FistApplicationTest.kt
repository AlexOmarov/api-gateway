package ru.somarov.gateway.tests.integration

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import ru.somarov.gateway.tests.unit.execute
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * First test class to check context switches between tests and classes
 * */
class FistApplicationTest {
    @Test
    fun `First healthcheck test in first class`() = execute { builder ->
        val response = builder.client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("UP", response.bodyAsText())
    }

    @Test
    fun `Second healthcheck test in first class`() = execute { builder ->
        val response = builder.client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("UP", response.bodyAsText())
    }
}
