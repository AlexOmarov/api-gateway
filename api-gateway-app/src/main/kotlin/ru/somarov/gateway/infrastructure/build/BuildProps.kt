package ru.somarov.gateway.infrastructure.build

import io.ktor.server.application.Application
import java.util.Properties

data class BuildProps(
    val version: String,
    val group: String,
    val artifact: String
) {
    companion object {
        fun parse(path: String): BuildProps {
            val load = Properties().also { props ->
                Application::class.java.getResourceAsStream(path)?.use { props.load(it) }
            }
            return BuildProps(
                version = load.getProperty("build.version", "undefined"),
                group = load.getProperty("build.group", "undefined"),
                artifact = load.getProperty("build.artifact", "undefined")
            )
        }
    }
}
