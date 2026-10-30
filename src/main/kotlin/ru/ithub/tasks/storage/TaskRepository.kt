package ru.ithub.tasks.storage

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import ru.ithub.tasks.model.BadRequestException
import ru.ithub.tasks.model.ConflictException
import ru.ithub.tasks.model.CreateTaskRequest
import ru.ithub.tasks.model.ForbiddenException
import ru.ithub.tasks.model.NotFoundException
import ru.ithub.tasks.model.Priority
import ru.ithub.tasks.model.Role
import ru.ithub.tasks.model.Task
import ru.ithub.tasks.model.UpdateTaskRequest
import ru.ithub.tasks.model.User

class TaskRepository(private val database: Database) {
    fun list(): List<Task> = transaction(database) {
        TasksTable.selectAll().map { it.toTask() }.sortedBy { it.id }
    }

    fun find(id: Int): Task? = transaction(database) {
        TasksTable.selectAll().where { TasksTable.id eq id }.singleOrNull()?.toTask()
    }

    fun isEmpty(): Boolean = transaction(database) {
        TasksTable.selectAll().limit(1).empty()
    }

    fun create(request: CreateTaskRequest, ownerId: Int): Task = transaction(database) {
        val title = normalizeTitle(request.title)
        val id = TasksTable.insert {
            it[TasksTable.title] = title
            it[done] = request.done
            it[priority] = request.priority.name
            it[TasksTable.ownerId] = ownerId
        }[TasksTable.id]
        Task(
            id = id,
            title = title,
            done = request.done,
            priority = request.priority,
            ownerId = ownerId,
        )
    }

    fun update(id: Int, request: UpdateTaskRequest, actor: User): Task = transaction(database) {
        val existing = TasksTable.selectAll().where { TasksTable.id eq id }.singleOrNull()?.toTask()
            ?: throw NotFoundException("Задача с id=$id не найдена")
        if (actor.role != Role.ADMIN && existing.ownerId != actor.id) {
            throw ForbiddenException("Можно изменять только свои задачи")
        }
        val title = normalizeTitle(request.title, ignoreId = id)
        TasksTable.update({ TasksTable.id eq id }) {
            it[TasksTable.title] = title
            it[done] = request.done
            it[priority] = request.priority.name
        }
        existing.copy(title = title, done = request.done, priority = request.priority)
    }

    fun delete(id: Int): Task = transaction(database) {
        val existing = TasksTable.selectAll().where { TasksTable.id eq id }.singleOrNull()?.toTask()
            ?: throw NotFoundException("Задача с id=$id не найдена")
        val removed = TasksTable.deleteWhere { TasksTable.id eq id }
        if (removed == 0) {
            throw NotFoundException("Задача с id=$id не найдена")
        }
        existing
    }

    fun seed(ownerId: Int) {
        if (!isEmpty()) {
            return
        }
        create(CreateTaskRequest("Изучить маршруты Ktor", done = false, priority = Priority.HIGH), ownerId)
        create(CreateTaskRequest("Подключить JSON-сериализацию", done = true, priority = Priority.MEDIUM), ownerId)
        create(CreateTaskRequest("Написать README", done = false, priority = Priority.LOW), ownerId)
    }

    private fun normalizeTitle(raw: String, ignoreId: Int? = null): String {
        val title = raw.trim()
        if (title.isEmpty()) {
            throw BadRequestException("Название задачи не может быть пустым")
        }
        if (title.length > MAX_TITLE_LENGTH) {
            throw BadRequestException("Название задачи не длиннее $MAX_TITLE_LENGTH символов")
        }
        val duplicate = TasksTable.selectAll().map { it.toTask() }.any { task ->
            task.id != ignoreId && task.title.equals(title, ignoreCase = true)
        }
        if (duplicate) {
            throw ConflictException("Задача с названием «$title» уже существует")
        }
        return title
    }

    private fun ResultRow.toTask(): Task = Task(
        id = this[TasksTable.id],
        title = this[TasksTable.title],
        done = this[TasksTable.done],
        priority = Priority.valueOf(this[TasksTable.priority]),
        ownerId = this[TasksTable.ownerId],
    )

    companion object {
        const val MAX_TITLE_LENGTH = 200
    }
}
