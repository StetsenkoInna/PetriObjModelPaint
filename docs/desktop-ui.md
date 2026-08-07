# petri-swing-ui — Desktop UI Guide

`petri-swing-ui` is a self-contained Swing desktop application for building, running and
inspecting Petri net simulations. No server or network connection required — it runs the
same `petri-math`/`petri-model` engine locally, in a single fat JAR.

## Running

```bash
java -jar petri-swing-ui/target/petri-swing-ui.jar
```

Build it first with `mvn package -DskipTests` (see the root [README](../README.md#build)).

---

## Visual Editor

Draw a net directly on the canvas: places, transitions, arcs, drag-and-drop layout.

| Class | Responsibility |
|-------|----------------|
| `ua.stetsenkoinna.graphpresentation.PetriNetsFrame` | Main application window |
| `ua.stetsenkoinna.graphpresentation.PetriNetsPanel` | Drawing canvas |
| `ua.stetsenkoinna.graphpresentation.SetPosition` / `SetTransition` / `SetArc` | Element property dialogs |

---

## Running a Simulation

| Class | Responsibility |
|-------|----------------|
| `ua.stetsenkoinna.graphpresentation.RunPetriObjModel` | Runs the model to completion, no animation |
| `ua.stetsenkoinna.graphpresentation.AnimRunPetriObjModel` / `AnimRunPetriSim` | Animated, step-by-step run |
| `ua.stetsenkoinna.graphpresentation.AnimationControls` | Play / pause / rewind toolbar |
| `ua.stetsenkoinna.graphpresentation.actions.*` | `PlayPauseAction`, `RewindAction`, `RunOneEventAction`, `StopSimulationAction`, `RunNetAction` |

Animation lets you watch tokens move through places and transitions fire in real time, useful
for checking that a net was drawn correctly before trusting its statistics.

---

## Statistics Module

Located under `ua.stetsenkoinna.graphpresentation.statistic`:

- `StatisticMonitorDialog` / `ChartSettingsDialog` — configure which places/transitions to
  track and how
- `LineChartBuilderService` / `ChartBuilderService` — render live charts during a run
- `StatisticGraphMonitor` / `StatisticConsoleMonitor` — collect per-element statistics
  (marking/buffer min, max, mean) as the simulation progresses
- `PetriStatisticFunction` — the aggregation functions available for a tracked element

---

## Petri-objects on the Canvas

A Petri-object is a named frame on the same canvas the nets are drawn on: what is inside it
belongs to it, and an arc crossing its border links it to another object. The **Model** menu
creates objects — around the current selection (`Ctrl+G`), empty, from a net library template,
or by duplicating one (`Ctrl+D`) — and edits the selected object's name and priority.

| Class | Responsibility |
|-------|----------------|
| `ua.stetsenkoinna.graphnet.GraphCanvasModel` | The canvas document: the drawing, its object frames, its shared places — and reading all that as a model |
| `ua.stetsenkoinna.graphnet.GraphObjectFrame` | One object's frame: move, resize, collapse, its name and priority |
| `ua.stetsenkoinna.graphnet.GraphPlaceFusion` | Two places joined into one shared place |
| `ua.stetsenkoinna.graphpresentation.objmodel.NetTemplateDialog` | Instantiating a net library template with arguments |

Run and Animate simulate the whole canvas, animation included — a token leaving one object is
seen arriving in another. A canvas without frames is a model of one object, so plain net
editing is unchanged.

**[docs/petri-object-models.md](petri-object-models.md)** — the full guide: what the links
mean, how a model is stored, and how to run one from code or over HTTP.

---

## Net Library / Reuse

Located under `ua.stetsenkoinna.graphreuse`:

- `GraphReUseFrame` / `GraphNetParametersFrame` — save a net as a reusable Java method and
  add it to the net library, then instantiate it as a sub-net elsewhere

`ua.stetsenkoinna.libnet.NetTemplateCatalog` is the other side of the same mechanism: it
lists the library methods that build nets and instantiates one with arguments, which is how
the editor turns `CreateNetSMOwithoutQueue(2, 0.5, "First")` into a Petri-object on the canvas.

This is the desktop-side entry point into the same `NetLibrary` /
`@NetLibraryMethod` mechanism described in the root README's
[Petri-object simulation technique](../README.md#petri-object-simulation-technique) section.

---

## PNML Import / Export

Supports import/export in PNML format (ISO/IEC 15909):

- **Import**: `File → Import PNML` (`Ctrl+I`)
- **Export**: `Save → Export to PNML` (`Ctrl+P`)

Import reads a whole Petri-object model too: every page of the document becomes a framed
object on the canvas, with the links between them restored as crossing arcs and shared
places. Export writes the canvas back the same way.

The same PNML format is accepted by `petri-server`'s `/api/v1/net/parse` and
`/api/v1/simulation/*` endpoints (see the [server integration guide](petri-server-integration.md)),
so a net built in the desktop editor can be exported and replayed on the server, and vice versa.
