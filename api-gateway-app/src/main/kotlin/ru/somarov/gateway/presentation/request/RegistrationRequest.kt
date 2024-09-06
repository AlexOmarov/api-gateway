package ru.somarov.gateway.presentation.request

import kotlinx.serialization.Serializable

@Serializable
data class RegistrationRequest(
    val email: String,
    val password: String?,
    val token: String?
)