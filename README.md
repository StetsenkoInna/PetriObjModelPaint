# petri-net-sim

Petri net graphical editor and simulator (v2.2.0), also known as
**PetriObjModelPaint**. Multi-module Maven project:

| Module | Purpose |
|--------|---------|
| `petri-math` | Core simulation engine |
| `petri-api` | Interfaces and DTOs (shared contract) |
| `petri-model` | Graph model, PNML parser |
| `petri-swing-ui` | Desktop editor (Swing, fat JAR) |
| `petri-server` | Spring Boot REST + WebSocket server |

## Petri-object simulation technique

petri-net-sim is the project of Petri-object simulation technique implementation. Petri-object simulation technique, the main concept of which is to compose the code of model of complicated discrete event system in a fast and flexible way, simultaneously providing fast running the simulation, is requisite. The behaviour description of the model is based on stochastic multichannel Petri net while the model composition is grounded on object-oriented technology. The Petri-object simulation software provides a scalable simulation algorithm, graphical editor, correct transformation of graphical images into a model, and correct simulation results.

In code terms, this technique lives in the `petri-math` module (`PetriObjModel`, `PetriSim`, `PetriP`, `PetriT`, `NetLibrary`): a Petri-object is built with class `PetriSim`, and several of them are composed into a model by declaring links — a shared place, a transition feeding a place of another object, or a place guarding a transition of another object. Once the list of Petri-objects is prepared and the links between them are set, the model is assembled with `PetriObjModel`, whose `go(double time)` method runs the simulation. `petri-model` carries the same model at graph level, so a composition is drawn in the editor, stored as a single PNML document and replayed by the server.

**[docs/petri-object-models.md](docs/petri-object-models.md)** — full guide: objects and links, composing a model in the editor, the PNML form, running one from code or over HTTP.

## Web app

**[petri-net-sim web app](https://github.com/sergiorbk/petri-net-sim)** — an
agent-powered super-app for building, generating and simulating Petri nets in the
browser, with AI features at its core: describe a production or queueing system in
plain language and an AI agent composes it into a net from a catalog of reusable
patterns; then edit it in the live graph editor, run stochastic simulations and
exchange models with this project over PNML.

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
Frame parts of a drawing as Petri-objects, link them across frame borders, and animate the
whole composition on one canvas.

**[docs/desktop-ui.md](docs/desktop-ui.md)** — full guide: editor, animation controls,
statistics module, Petri-objects on the canvas, net library, PNML import/export.

---

## Server (Spring Boot)

```bash
java -jar petri-server/target/petri-server.jar
# or: mvn spring-boot:run -pl petri-server
```

REST + SSE + WebSocket API for running simulations from external systems (web frontends,
Python backends, microservices). Starts at `http://localhost:8080`, interactive docs at
`http://localhost:8080/docs`. `/api/v1` runs a single net; `/api/v2` runs a Petri-object
model and reports statistics per object.

**[docs/petri-server-integration.md](docs/petri-server-integration.md)** — full guide:
REST API reference, SSE streaming, WebSocket/STOMP, session control, PNML requirements,
the Petri-object model API.

---

## Project Structure

```
petri-net-sim/
├── petri-math/        # Simulation engine (PetriObj, LibNet, utils)
├── petri-api/         # Interfaces and DTOs
├── petri-model/       # Graph model, PNML, config
├── petri-swing-ui/    # Swing desktop editor
├── petri-server/      # Spring Boot server
└── pom.xml            # Parent POM
```

---

## License

This project is licensed per module (see the `LICENSE` file in each module directory):

| Modules | License |
|---------|---------|
| `petri-math`, `petri-api`, `petri-model` | [MIT](petri-math/LICENSE) |
| `petri-swing-ui`, `petri-server` | [PolyForm Noncommercial 1.0.0](petri-swing-ui/LICENSE) |

The simulation engine and model libraries stay free for any use, including commercial.
The desktop editor and the simulation server may be used for noncommercial purposes only:
personal use, research, education, and use by noncommercial organizations are all permitted.
For commercial licensing of these modules, contact <inna.stetsenko-fiot@edu.kpi.ua> or
<sergey24rybak@gmail.com>.
