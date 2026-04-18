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

### Мережа

| Метод | Шлях | Тіло / Відповідь |
|-------|------|-----------------|
| `POST` | `/api/v1/net/parse` | `{ "netXml": "..." }` → місця, переходи, дуги з координатами |

### Симуляція

| Метод | Шлях | Тіло / Відповідь |
|-------|------|-----------------|
| `POST` | `/api/v1/simulation/start` | `{ "netXml": "...", "simulationTime": 100 }` → `{ "sessionId": "..." }` |
| `POST` | `/api/v1/simulation/stream` | `{ "netXml": "..." }` → `text/event-stream` (див. нижче) |
| `POST` | `/api/v1/simulation/{id}/pause` | — |
| `POST` | `/api/v1/simulation/{id}/resume` | — |
| `POST` | `/api/v1/simulation/{id}/stop` | — |
| `GET`  | `/api/v1/simulation/{id}/status` | `{ "status": "RUNNING" \| "PAUSED" \| "FINISHED" \| ... }` |
| `GET`  | `/api/v1/simulation/{id}/result` | Агрегована статистика після завершення; 202 поки виконується |

Статуси: `PENDING` `RUNNING` `PAUSED` `FINISHED` `HALTED`

### Формат результату

```json
{
  "simulation_time": 3600, "final_time": 3600, "total_steps": 18432,
  "places":      [{ "id": "p1", "name": "Queue", "final_marking": 2, "mean_marking": 1.73, "observed_min": 0, "observed_max": 8 }],
  "transitions": [{ "id": "t1", "name": "Service", "final_buffer": 0, "mean_buffer": 0.87, "observed_min": 0, "observed_max": 3 }]
}
```

### Healthcheck

| Метод | Шлях | Опис |
|-------|------|------|
| `GET` | `/health` | Spring Boot Actuator health check |

### SSE-стрімінг (`/stream`)

Query-параметри: `simulationTime` (за замовчуванням 3600) · `timeStep` (за замовчуванням 1.0) · `snapshotInterval` · `animationDelayMs` (за замовчуванням 0)

Формат кожної SSE-події:
```json
{ "current_time": 1.0, "step_number": 42, "markings": {"p1": 3}, "buffers": {"t1": 1}, "progress": 0.001 }
```
Потік завершується рядком `data: [DONE]`. Перша подія (`event: session`) містить `sessionId` для подальших запитів керування.

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

## Посібник з інтеграції

Повна документація з інтеграції `petri-server` у зовнішні системи (веб-фронтенди,
Python-бекенди, мікросервіси) — на англійській мові:

**[docs/petri-server-integration.md](docs/petri-server-integration.md)**

Містить: SSE-стрімінг з прикладами коду (JS, Python, curl), WebSocket/STOMP,
керування сесією, вимоги до PNML, порівняння транспортів та нотатки щодо інтеграції з text2pnml.

---

## PNML

Підтримується імпорт/експорт у форматі PNML (ISO/IEC 15909).

- **Імпорт**: File → Import PNML (Ctrl+I)
- **Експорт**: Save → Export to PNML (Ctrl+P)
