package ru.ithub.tasks.routes

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import ru.ithub.tasks.model.BadRequestException
import ru.ithub.tasks.model.CreateTaskRequest
import ru.ithub.tasks.model.NotFoundException
import ru.ithub.tasks.model.ServiceInfo
import ru.ithub.tasks.model.TaskEvent
import ru.ithub.tasks.model.UpdateTaskRequest
import ru.ithub.tasks.model.UserResponse
import ru.ithub.tasks.security.AuthService
import ru.ithub.tasks.security.JwtService
import ru.ithub.tasks.security.NotificationHub
import ru.ithub.tasks.security.requireAdmin
import ru.ithub.tasks.security.requireUser
import ru.ithub.tasks.storage.TaskRepository
import ru.ithub.tasks.storage.UserRepository

fun Application.configureRouting(
    tasks: TaskRepository,
    users: UserRepository,
    auth: AuthService,
    jwt: JwtService,
    hub: NotificationHub,
) {
    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml") {
            outputPath = "build/openapi-ui"
        }
        get("/") {
            call.respond(
                ServiceInfo(
                    name = "ktor-tasks-platform",
                    description = "Задачи, PostgreSQL, роли, JWT, WebSocket и Swagger",
                ),
            )
        }
        post("/register") {
            val created = auth.register(call.receive())
            call.respond(HttpStatusCode.Created, created)
        }
        post("/login") {
            call.respond(auth.login(call.receive()))
        }
        authenticate("auth-jwt") {
            get("/users") {
                call.requireAdmin(users)
                val visible = users.list().map { user ->
                    UserResponse(id = user.id, login = user.login, role = user.role)
                }
                call.respond(visible)
            }
        }
        webSocket("/notifications") {
            val userId = jwt.userIdFrom(call.request.queryParameters["token"])
            if (userId == null || users.findById(userId) == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Нужен действительный token"))
                return@webSocket
            }
            hub.join(this)
            try {
                send(Frame.Text("""{"type":"connected"}"""))
                for (frame in incoming) {
                    if (frame is Frame.Close) {
                        break
                    }
                }
            } finally {
                hub.leave(this)
            }
        }
        route("/tasks") {
            get {
                call.respond(tasks.list())
            }
            get("/{id}") {
                val id = call.requireTaskId()
                val task = tasks.find(id) ?: throw NotFoundException("Задача с id=$id не найдена")
                call.respond(task)
            }
            authenticate("auth-jwt") {
                post {
                    val owner = call.requireUser(users)
                    val created = tasks.create(call.receive<CreateTaskRequest>(), owner.id)
                    hub.broadcast(TaskEvent(type = "task.created", task = created))
                    call.response.header(HttpHeaders.Location, "/tasks/${created.id}")
                    call.respond(HttpStatusCode.Created, created)
                }
                put("/{id}") {
                    val actor = call.requireUser(users)
                    val updated = tasks.update(call.requireTaskId(), call.receive<UpdateTaskRequest>(), actor)
                    hub.broadcast(TaskEvent(type = "task.updated", task = updated))
                    call.respond(updated)
                }
                delete("/{id}") {
                    call.requireAdmin(users)
                    val removed = tasks.delete(call.requireTaskId())
                    hub.broadcast(TaskEvent(type = "task.deleted", task = removed))
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun ApplicationCall.requireTaskId(): Int {
    val id = parameters["id"]?.toIntOrNull()
    if (id == null || id <= 0) {
        throw BadRequestException("Параметр path id должен быть положительным целым числом")
    }
    return id
}
