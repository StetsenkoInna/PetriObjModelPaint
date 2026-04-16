# PetriObjModelPaint

Графічний редактор та симулятор мереж Петрі (v2.0.0). Багатомодульний Maven-проєкт:

| Модуль | Призначення |
|--------|------------|
| `petri-math` | Математичне ядро симуляції |
| `petri-api` | Інтерфейси та DTO (спільний контракт) |
| `petri-model` | Граф-модель, парсер PNML |
| `petri-swing-ui` | Десктопний редактор (Swing, fat JAR) |
| `petri-server` | Spring Boot REST + WebSocket сервер |

## Вимоги

- Java 23+
- Maven 3.9+

---

## Збірка

```bash
mvn package -DskipTests
```

Результат:
- `petri-swing-ui/target/petri-swing-ui.jar`
- `petri-server/target/petri-server.jar`

---

## Запуск десктопного UI (Swing)

```bash
java -jar petri-swing-ui/target/petri-swing-ui.jar
```

---

## Запуск сервера

```bash
java -jar petri-server/target/petri-server.jar
```

Або через Maven (перебудовує перед запуском):

```bash
mvn spring-boot:run -pl petri-server
```

Сервер стартує на `http://localhost:8080`.

| URL | Опис |
|-----|------|
| `http://localhost:8080/docs` | Swagger UI (інтерактивна документація API) |
| `http://localhost:8080/openapi.json` | OpenAPI специфікація (JSON) |

---

## REST API (v1)

| Метод | Шлях | Тіло / Відповідь |
|-------|------|-----------------|
| `POST` | `/api/v1/simulation/start` | `{ "netXml": "...", "simulationTime": 100 }` → `{ "sessionId": "..." }` |
| `POST` | `/api/v1/simulation/{id}/pause` | — |
| `POST` | `/api/v1/simulation/{id}/resume` | — |
| `POST` | `/api/v1/simulation/{id}/stop` | — |
| `GET`  | `/api/v1/simulation/{id}/status` | `{ "status": "RUNNING" }` |

Статуси: `PENDING` `RUNNING` `PAUSED` `FINISHED` `HALTED`

---

## WebSocket (STOMP over SockJS)

Endpoint: `ws://localhost:8080/ws`

| Напрямок | Адреса |
|----------|--------|
| Підписка на кроки | `/topic/v1/sim/{id}/steps` |
| Підписка на статус | `/topic/v1/sim/{id}/status` |
| Керування (клієнт → сервер) | `/app/v1/sim/{id}/control` з тілом `PAUSE` / `RESUME` / `STOP` |

---

## Структура проєкту

```
PetriObjModelPaint/
├── petri-math/        # Ядро симуляції (PetriObj, LibNet, utils)
├── petri-api/         # Інтерфейси та DTO
├── petri-model/       # Граф-модель, PNML, конфіг
├── petri-swing-ui/    # Десктопний Swing-редактор
├── petri-server/      # Spring Boot сервер
└── pom.xml            # Parent POM
```

---

## PNML

Підтримується імпорт/експорт у форматі PNML (ISO/IEC 15909).

- **Імпорт**: File → Import PNML (Ctrl+I)
- **Експорт**: Save → Export to PNML (Ctrl+P)
