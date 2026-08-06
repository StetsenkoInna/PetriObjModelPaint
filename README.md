# PetriObjModelPaint

Petri net graphical editor and simulator (v2.0.1). Multi-module Maven project:

| Module | Purpose |
|--------|---------|
| `petri-math` | Core simulation engine |
| `petri-api` | Interfaces and DTOs (shared contract) |
| `petri-model` | Graph model, PNML parser |
| `petri-swing-ui` | Desktop editor (Swing, fat JAR) |
| `petri-server` | Spring Boot REST + WebSocket server |

## Petri-object simulation technique

PetriObjModelPaint is the project of Petri-object simulation technique implementation. Petri-object simulation technique, the main concept of which is to compose the code of model of complicated discrete event system in a fast and flexible way, simultaneously providing fast running the simulation, is requisite. The behaviour description of the model is based on stochastic multichannel Petri net while the model composition is grounded on object-oriented technology. The Petri-object simulation software provides a scalable simulation algorithm, graphical editor, correct transformation of graphical images into a model, and correct simulation results.

In code terms, this technique lives in the `petri-math` module (`PetriObjModel`, `PetriSim`, `PetriP`, `PetriT`, `NetLibrary`): a Petri-object is built with class `PetriSim`, several Petri-objects are linked together by combining places (`linkObjectsCombiningPlaces`), and passing tokens between the transition of one Petri-object and the place of another is programmed via `PetriSim.DoT()`. Once the list of Petri-objects is prepared and the links between them are set, the model is assembled with `PetriObjModel`, whose `go(double time)` method runs the simulation. The `petri-model` module and PNML format are only the graph-level interchange representation used by the editor and the API — at simulation time the graph is turned into this object model and executed by `petri-math`, in both the desktop UI and the server.

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

## Desktop UI (Swing)

```bash
java -jar petri-swing-ui/target/petri-swing-ui.jar
```

Self-contained visual editor and simulator, no server required: draw a net, run it with
live animation, watch statistics charts, save nets to the net library and import/export PNML.

**[docs/desktop-ui.md](docs/desktop-ui.md)** — full guide: editor, animation controls,
statistics module, net library, PNML import/export.

---

## Server (Spring Boot)

```bash
java -jar petri-server/target/petri-server.jar
# or: mvn spring-boot:run -pl petri-server
```

REST + SSE + WebSocket API for running simulations from external systems (web frontends,
Python backends, microservices). Starts at `http://localhost:8080`, interactive docs at
`http://localhost:8080/docs`.

**[docs/petri-server-integration.md](docs/petri-server-integration.md)** — full guide:
REST API reference, SSE streaming, WebSocket/STOMP, session control, PNML requirements,
text2pnml integration.

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
