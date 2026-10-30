<div align="center">

# PetriObjModelPaint

**A graphical editor and simulator for Petri nets and Petri-object models.**

Draw a net, or frame parts of it as linked Petri-objects to compose a larger model, then run it
with live animation, watch the statistics, and exchange it as PNML.

[![License](https://img.shields.io/badge/license-MIT_%2F_PolyForm_NC-1f6feb?style=flat-square)](#license)
[![PNML](https://img.shields.io/badge/PNML-ISO%2FIEC_15909--2-2ea043?style=flat-square)](docs/petri-object-models.md)
[![petri-net-sim](https://img.shields.io/badge/petri--net--sim-web_app-2563eb?style=flat-square&logo=data:image/svg%2Bxml%3Bbase64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAzMiAzMiI+PGNpcmNsZSBjeD0iMTYiIGN5PSIxNiIgcj0iMTEiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzI1NjNlYiIgc3Ryb2tlLXdpZHRoPSI0Ii8+PGNpcmNsZSBjeD0iMTYiIGN5PSIxNiIgcj0iNC41IiBmaWxsPSIjMjU2M2ViIi8+PC9zdmc+&logoColor=white)](https://github.com/sergiorbk/petri-net-sim)

![Java 23](https://img.shields.io/badge/Java_23-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white)

![Drawing and simulating a Petri net](docs/media/demo-petri-model.gif)

</div>

---

> [!TIP]
> **Just want to use the app?** Grab the ready-made editor, no source build required:
> [Windows](https://github.com/StetsenkoInna/PetriObjModelPaint/releases/download/v2.5.0/petri-swing-ui-2.5.0-windows.zip) ·
> [Linux](https://github.com/StetsenkoInna/PetriObjModelPaint/releases/download/v2.5.0/petri-swing-ui-2.5.0-linux.zip) ·
> [macOS](https://github.com/StetsenkoInna/PetriObjModelPaint/releases/download/v2.5.0/petri-swing-ui-2.5.0-macos.zip)
> (or browse the [latest release](https://github.com/StetsenkoInna/PetriObjModelPaint/releases/latest) page for other
> versions). Unzip it and run the launcher inside (`.bat` / `.sh` / `.command`): it checks for
> Java 23+ and points you to the official download if it's missing.

> [!NOTE]
> **Want to contribute?** Open an issue to discuss the change first, then create a
> branch, open a pull request against `master`, and follow the steps in
> [CONTRIBUTING.md](CONTRIBUTING.md).

This is a multi-module Maven project:

| Module | Purpose |
|--------|---------|
| `petri-math` | Core simulation engine |
| `petri-api` | Interfaces and DTOs (shared contract) |
| `petri-model` | Graph model, PNML parser |
| `petri-swing-ui` | Desktop editor (Swing, fat JAR) |
| `petri-server` | Spring Boot REST + WebSocket server |

## Reusable libraries

From v2.4.0, releases include [petri-math](https://github.com/StetsenkoInna/PetriObjModelPaint/releases/download/v2.5.0/petri-math-2.5.0.jar), [petri-api](https://github.com/StetsenkoInna/PetriObjModelPaint/releases/download/v2.5.0/petri-api-2.5.0.jar) and [petri-model](https://github.com/StetsenkoInna/PetriObjModelPaint/releases/download/v2.5.0/petri-model-2.5.0.jar) for use without the editor. See the [Java library integration guide](docs/java-libraries.md) for Maven setup, requirements and examples.

---

## Two ways to run it

| | |
|---|---|
| **Desktop UI (Swing)** | Self-contained visual editor and simulator, no server required |
| **Server (Spring Boot)** | REST + SSE + WebSocket API for running simulations from external systems |

Both run the same model underneath, whether it's a single net or several composed into a
Petri-object model.

Shared modeling guidance lives in
[petri-modeling-skills](https://github.com/sergiorbk/petri-modeling-skills):
rules for correct Petri nets, production and queueing blocks, and PNML interchange.
The desktop MCP integration and [petri-net-sim](https://github.com/sergiorbk/petri-net-sim)
read the same versioned guides.

**Desktop UI.**

```bash
mvn package -DskipTests
```

```bash
java -jar petri-swing-ui/target/petri-swing-ui.jar
```

Draw a net, run it with live animation, watch statistics charts, save nets to the net library
and import/export PNML. Frame parts of a drawing as Petri-objects, link them across frame
borders, and animate the whole composition on one canvas.

![Composing and running a Petri-object model](docs/media/demo-petri-object-model.gif)

**Server.**

```bash
mvn package -DskipTests
```

```bash
java -jar petri-server/target/petri-server.jar
# or: mvn spring-boot:run -pl petri-server
```

Starts at `http://localhost:8080`, interactive docs at `http://localhost:8080/docs`. `/api/v1`
runs a single net; `/api/v2` runs a Petri-object model and reports statistics per object.

---

## Petri-object simulation technique

PetriObjModelPaint is the project of Petri-object simulation technique implementation. Petri-object simulation technique, the main concept of which is to compose the code of model of complicated discrete event system in a fast and flexible way, simultaneously providing fast running the simulation, is requisite. The behaviour description of the model is based on stochastic multichannel Petri net while the model composition is grounded on object-oriented technology. The Petri-object simulation software provides a scalable simulation algorithm, graphical editor, correct transformation of graphical images into a model, and correct simulation results.

In code terms, this technique lives in the `petri-math` module (`PetriObjModel`, `PetriSim`, `PetriP`, `PetriT`, `NetLibrary`): a Petri-object is built with class `PetriSim` from a Petri net, and one net drawn in the graphical editor and saved to the net library is reused to create a whole group of Petri-objects, either with the same parameters or with different ones passed through the Petri-object's constructor. Several Petri-objects are then composed into a model by declaring links between them: a place shared by two objects, or a transition of one object feeding a place of another. Once the list of Petri-objects is prepared and the links are set, the model is assembled with `PetriObjModel`, whose `go(double time)` method runs the simulation. `petri-model` carries the same model at graph level, so a composition is drawn in the editor, stored as a single PNML document and replayed by the server.

---

## Requirements

- Java 23+
- Maven 3.9+

---

## Build

Build the application. Maven downloads the pinned shared modeling resource archive
from GitHub Releases and verifies its SHA-256 automatically:

```bash
mvn package -DskipTests
```

The desktop JAR includes the shared guides from
[petri-modeling-skills](https://github.com/sergiorbk/petri-modeling-skills).
They are available through MCP resources and prompts without a runtime network connection.

Output:
- `petri-swing-ui/target/petri-swing-ui.jar`
- `petri-server/target/petri-server.jar`

---

## Documentation

| Guide | What it covers |
|-------|----------------|
| [Desktop UI](docs/desktop-ui.md) | Editor, animation controls, statistics module, Petri-objects on the canvas, net library, PNML import/export |
| [Desktop MCP](docs/desktop-mcp.md) | Connect Codex, Claude Code or Claude Desktop to the running Swing editor |
| [Java libraries](docs/java-libraries.md) | Import the simulation engine, API and PNML model without the editor |
| [Training data and review](docs/training-data-export.md) | Exchange versioned model/image ZIPs, review and compare revisions, or configure PNG exports |
| [Petri-object models](docs/petri-object-models.md) | Objects and links, composing a model in the editor, the PNML form, running one from code or over HTTP |
| [Server integration](docs/petri-server-integration.md) | REST API reference, SSE streaming, WebSocket/STOMP, session control, PNML requirements, the Petri-object model API |

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

## Web app

**[petri-net-sim web app](https://github.com/sergiorbk/petri-net-sim)**: an
agent-powered super-app for building, generating and simulating Petri nets in the
browser, with AI features at its core. Describe a production or queueing system in
plain language and an AI agent composes it into a net from a catalog of reusable
patterns; then edit it in the live graph editor, run stochastic simulations and
exchange models with this project over PNML.

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
