package ru.ithub.tasks.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import ru.ithub.tasks.model.ConflictException
import ru.ithub.tasks.model.ErrorResponse
import ru.ithub.tasks.model.ForbiddenException
import ru.ithub.tasks.model.NotFoundException
import ru.ithub.tasks.model.UnauthorizedException
import ru.ithub.tasks.model.BadRequestException as ApiBadRequestException

private const val INVALID_JSON = "Некорректное JSON-тело запроса"

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiBadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", cause.message ?: "Некорректный запрос"))
        }
        exception<UnauthorizedException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", cause.message ?: "Не авторизован"))
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", cause.message ?: "Недостаточно прав"))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", cause.message ?: "Не найдено"))
        }
        exception<ConflictException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse("conflict", cause.message ?: "Конфликт"))
        }
        exception<BadRequestException> { call, cause ->
            val message = if (cause.hasSerializationCause()) INVALID_JSON else cause.message ?: "Некорректный запрос"
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", message))
        }
        exception<SerializationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", INVALID_JSON))
        }
        exception<ContentConvertException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", INVALID_JSON))
        }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) {
                throw cause
            }
            call.application.log.error("Необработанная ошибка", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", "Внутренняя ошибка сервера"),
            )
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Маршрут не найден"))
        }
        status(HttpStatusCode.UnsupportedMediaType) { call, _ ->
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                ErrorResponse("unsupported_media_type", "Ожидается Content-Type: application/json"),
            )
        }
    }
}

private fun Throwable.hasSerializationCause(): Boolean {
    val seen = HashSet<Throwable>()
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        if (current is SerializationException || current is ContentConvertException) {
            return true
        }
        current = current.cause
    }
    return false
}
