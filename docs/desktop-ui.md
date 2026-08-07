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
belongs to it. Every object action is reached by **right-clicking** whatever it applies to —
a selection, an existing frame, or empty canvas — there is no separate menu. `Ctrl+A` selects
every frame along with every element; `Delete` and `Ctrl+D` act on whatever frames are selected
the same way they already act on elements. Right-clicking a place or transition itself does
nothing — only double-click opens it, and only a left-click ever selects it.

Once elements belong to a frame they are locked on the shared canvas; the object's own net is
edited in a window of its own, opened by double-clicking anywhere on the frame or via its
right-click **Edit net...**. That window operates on the same element instances the canvas
holds; **Save** applies whatever changed — additions, removals, anything moved — back onto the
canvas and refits the frame's outline to match, **Cancel** restores every element to where it
was and applies nothing.

The eye icon in a frame's header shows or hides its own drawing, independent of the frame's
size — the elements still exist and still hold their marking either way. A locked object
connects to others by dragging from one of its own places or transitions the same way a free
element does; while its content is hidden that means dragging from its **port** instead — a
small labelled circle standing in for the element, drawn on the frame's border only while there
is nothing else on screen for it to stand in for. Either way the resulting link is drawn as an
ordinary, border-trimmed arc, anchored to the port only on whichever end is actually hidden.

| Class | Responsibility |
|-------|----------------|
| `ua.stetsenkoinna.graphnet.GraphCanvasModel` | The canvas document: the drawing, its object frames, its shared places, its ports — and reading all that as a model |
| `ua.stetsenkoinna.graphnet.GraphObjectFrame` | One object's frame: move, resize, collapse, show/hide its content, its name and priority |
| `ua.stetsenkoinna.graphnet.FramePort` | One port on a frame's border, standing in for a locked place or transition |
| `ua.stetsenkoinna.graphnet.PortAnchor` | A fixed point an arc can trim its line to exactly like a real place — a port, or the pointer mid-drag |
| `ua.stetsenkoinna.graphnet.GraphPlaceFusion` | Two places joined into one shared place |
| `ua.stetsenkoinna.graphpresentation.objmodel.ObjectEditorFrame` | The window one Petri-object's own net is edited in, with its own Save/Cancel |
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
