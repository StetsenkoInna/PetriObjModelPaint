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

## Net Library / Reuse

Located under `ua.stetsenkoinna.graphreuse`:

- `GraphReUseFrame` / `GraphNetParametersFrame` — save a net as a reusable Java method and
  add it to the net library, then instantiate it as a sub-net elsewhere

This is the desktop-side entry point into the same `NetLibrary` /
`@NetLibraryMethod` mechanism described in the root README's
[Petri-object simulation technique](../README.md#petri-object-simulation-technique) section.

---

## PNML Import / Export

Supports import/export in PNML format (ISO/IEC 15909):

- **Import**: `File → Import PNML` (`Ctrl+I`)
- **Export**: `Save → Export to PNML` (`Ctrl+P`)

The same PNML format is accepted by `petri-server`'s `/api/v1/net/parse` and
`/api/v1/simulation/*` endpoints (see the [server integration guide](petri-server-integration.md)),
so a net built in the desktop editor can be exported and replayed on the server, and vice versa.
