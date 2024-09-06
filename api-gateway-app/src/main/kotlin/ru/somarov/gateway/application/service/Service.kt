package ru.somarov.gateway.application.service

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.payload.Payload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import ru.somarov.gateway.infrastructure.rsocket.client.Client
import ru.somarov.gateway.presentation.request.RegistrationRequest
import ru.somarov.gateway.presentation.response.RegistrationResponse

class Service(private val client: Client) {

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun register(request: RegistrationRequest): RegistrationResponse {
        return Cbor.Default.decodeFromByteArray<RegistrationResponse>(
            client.requestResponse(
                Payload(
                    ByteReadPacket(
                        Cbor.Default.encodeToByteArray(
                            request
                        )
                    )
                )
            ).data.readBytes()
        )
    }
}
