# PetriObjModelPaint

Petri net graphical editor and simulator (v2.0.0). Multi-module Maven project:

| Module | Purpose |
|--------|---------|
| `petri-math` | Core simulation engine |
| `petri-api` | Interfaces and DTOs (shared contract) |
| `petri-model` | Graph model, PNML parser |
| `petri-swing-ui` | Desktop editor (Swing, fat JAR) |
| `petri-server` | Spring Boot REST + WebSocket server |

## Requirements

- Java 23+
- Maven 3.9+

---

## Build

```bash
mvn package -DskipTests
```

Output:
- `petri-swing-ui/target/petri-swing-ui.jar`
- `petri-server/target/petri-server.jar`

---

## Run the Desktop UI (Swing)

```bash
java -jar petri-swing-ui/target/petri-swing-ui.jar
```

---

## Run the Server

```bash
java -jar petri-server/target/petri-server.jar
```

Or via Maven (rebuilds before starting):

```bash
mvn spring-boot:run -pl petri-server
```

Server starts at `http://localhost:8080`.

| URL | Description |
|-----|-------------|
| `http://localhost:8080/docs` | Swagger UI (interactive API docs) |
| `http://localhost:8080/openapi.json` | OpenAPI spec (JSON) |

---

## REST API (v1)

| Method | Path | Body / Response |
|--------|------|----------------|
| `POST` | `/api/v1/simulation/start` | `{ "netXml": "...", "simulationTime": 100 }` → `{ "sessionId": "..." }` |
| `POST` | `/api/v1/simulation/{id}/pause` | — |
| `POST` | `/api/v1/simulation/{id}/resume` | — |
| `POST` | `/api/v1/simulation/{id}/stop` | — |
| `GET`  | `/api/v1/simulation/{id}/status` | `{ "status": "RUNNING" }` |

Statuses: `PENDING` `RUNNING` `PAUSED` `FINISHED` `HALTED`

---

## WebSocket (STOMP over SockJS)

Endpoint: `ws://localhost:8080/ws`

| Direction | Address |
|-----------|---------|
| Subscribe to steps | `/topic/v1/sim/{id}/steps` |
| Subscribe to status | `/topic/v1/sim/{id}/status` |
| Control (client → server) | `/app/v1/sim/{id}/control` with body `PAUSE` / `RESUME` / `STOP` |

---

## Project Structure

```
PetriObjModelPaint/
├── petri-math/        # Simulation engine (PetriObj, LibNet, utils)
├── petri-api/         # Interfaces and DTOs
├── petri-model/       # Graph model, PNML, config
├── petri-swing-ui/    # Swing desktop editor
├── petri-server/      # Spring Boot server
└── pom.xml            # Parent POM
```

---

## PNML

Import/export support for PNML format (ISO/IEC 15909).

- **Import**: File → Import PNML (Ctrl+I)
- **Export**: Save → Export to PNML (Ctrl+P)
