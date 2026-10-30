package ru.ithub.tasks.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

fun Application.configureRequestLogging() {
    install(CallLogging) {
        level = Level.INFO
        disableDefaultColors()
        format { call ->
            val status = call.response.status()?.value?.toString() ?: "-"
            "${call.request.httpMethod.value} ${call.request.path()} -> $status (${call.processingTimeMillis()} ms)"
        }
    }
}
