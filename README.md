# Платформа задач

Один Ktor-сервис объединяет CRUD задач, регистрацию, JWT, роли `USER` и `ADMIN`, хранение в базе, Swagger и уведомления по WebSocket. Локальный запуск без Docker пишет в файл H2. `docker compose` поднимает тот же сервис рядом с PostgreSQL.

## Архитектура

Запрос проходит Netty, журнал `CallLogging` и общий обработчик `StatusPages`. JSON читает и пишет `ContentNegotiation` с kotlinx.serialization. Схема таблиц `users` и `tasks` создаётся через JetBrains Exposed при старте.

| Слой | Где лежит | Зачем |
| --- | --- | --- |
| Маршруты | `routes/TaskRoutes.kt` | HTTP и WebSocket |
| Аутентификация | `security/` | BCrypt, JWT, проверка роли по записи в базе |
| Уведомления | `security/NotificationHub.kt` | Рассылка события всем открытым сокетам после записи в базу |
| Хранение | `storage/` | Exposed, PostgreSQL в Docker и H2 локально и в тестах |
| Контракт | `src/main/resources/openapi/documentation.yaml` | Swagger UI и HTML OpenAPI |

Роль в токене есть, но доступ к защищённым маршрутам сверяется с ролью в таблице `users`. Регистрация всегда создаёт `USER`: поле `role` в теле запроса игнорируется.

| Метод и путь | Кто может вызвать |
| --- | --- |
| `GET /tasks`, `GET /tasks/{id}` | все |
| `POST /register`, `POST /login` | все |
| `POST /tasks` | любой действительный токен, владелец — сам пользователь |
| `PUT /tasks/{id}` | владелец задачи или `ADMIN`, иначе 403; если задачи нет — 404 |
| `DELETE /tasks/{id}` | только `ADMIN`; `USER` получает 403 даже если задачи нет |
| `GET /users` | только `ADMIN`, без хэшей паролей |
| `GET /notifications?token=` | WebSocket для любого действительного JWT |

Коды ошибок приходят JSON-объектом `{ "error", "message" }`: `bad_request` 400, `unauthorized` 401, `forbidden` 403, `not_found` 404, `conflict` 409.

При пустой таблице задач сервис создаёт администратора `admin` / `admin123` и три учебные задачи. Пароль хранится как BCrypt-хэш. В JWT есть срок действия и claims `userId`, `login`, `role`.

WebSocket `/notifications?token=<JWT>` сначала шлёт `{"type":"connected"}`. После создания, изменения или удаления задачи все подключённые клиенты получают `{ "type", "task" }`, где `type` равен `task.created`, `task.updated` или `task.deleted`.

## Запуск локально

Нужны JDK 17 и ничего больше: без `DATABASE_URL` сервис сам создаёт файл `./data/tasks` (H2 в режиме PostgreSQL).

```bat
gradlew.bat run
```

Сервер слушает `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger`. Спецификация: `http://localhost:8080/swagger/documentation.yaml`. HTML-страница OpenAPI: `http://localhost:8080/openapi`.

Регистрация и вход:

```bat
curl -H "Content-Type: application/json" -d "{\"login\":\"student\",\"password\":\"secret12\"}" http://localhost:8080/register
curl -H "Content-Type: application/json" -d "{\"login\":\"student\",\"password\":\"secret12\"}" http://localhost:8080/login
```

Дальше заголовок `Authorization: Bearer <token>`. Администратор входит как `admin` / `admin123`.

Уведомления из браузерной консоли:

```javascript
const socket = new WebSocket("ws://localhost:8080/notifications?token=" + token);
socket.onmessage = (event) => console.log(event.data);
```

Остановить сервер: Ctrl+C в том же окне.

## Тесты

```bat
gradlew.bat test
```

Отчёт: `build/reports/tests/test/index.html`.

Тесты поднимают отдельную память H2 на каждый сценарий и не трогают PostgreSQL. Они проверяют CRUD, хэш пароля, claim `role`, ответы 401 и 403, состав OpenAPI, строки журнала `GET /tasks -> 200` и `POST /tasks -> 401`, а также события WebSocket `task.created` и `task.deleted`.

## Деплой

Нужны Docker и Docker Compose v2. Из каталога проекта:

```bat
docker compose up --build
```

Compose ждёт, пока PostgreSQL 16 ответит на `pg_isready`, затем запускает приложение. Образ собирается в два шага: Temurin 17 JDK собирает дистрибутив, Temurin 17 JRE его запускает. Приложение слушает `0.0.0.0:8080` и подключается к `jdbc:postgresql://db:5432/tasks` (пользователь и пароль базы `tasks`). Секрет JWT задаётся переменной `JWT_SECRET` и не короче 32 байт.

Проверка после старта: `http://localhost:8080/swagger` и `POST http://localhost:8080/login` с телом `{"login":"admin","password":"admin123"}`.

Остановка:

```bat
docker compose down
```

Каталог `data/` и сборка `build/` в git не входят.
