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
for checking that a net was drawn correctly before trusting its statistics. On a canvas composed
of Petri-objects, the object currently firing has its frame lit up, and a link crossing to
another object — or to a free element — lights up both ends together in a second colour, so
which object is active, and where a token just crossed to, is legible at a glance.

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
a selection, an existing frame, or empty canvas - there is no separate menu. Right-clicking a
place or transition itself does nothing: only double-click opens it, and only a left-click ever
selects it.

The canvas has one notion of the things it holds, so an operation is written once and reaches
both kinds. `Ctrl+A` selects every object on the active canvas along with every element of it;
`Delete`, `Ctrl+C`/`Ctrl+V`, `Ctrl+D`, `Ctrl+L`, the rubber band and the eraser all act on
whatever objects are selected the same way they act on elements. The rubber band catches an
object by its centre, exactly like a place. `Ctrl+Z` reaches object creation and removal too.

### Editing an object in place

Once elements belong to a frame they are locked on the canvas above it. The object's own net is
edited **in place**: double-click the frame, or use its right-click **Open this object's
canvas**, and the main canvas switches to that object's own level. A strip along the bottom of
the canvas area lists the open canvases, one pill each, badged with the level - `0 Net`,
`1 Machine`, `2 Buffer` - and since a canvas is never opened without the canvases enclosing it,
the pill order is the breadcrumb. The strip is hidden while the document has no objects at all.

On an object's own canvas its frame is drawn expanded around its net, so the boundary being
edited inside is visible, and its own members are directly draggable, deletable and arc-able.
Everything else - the free elements, sibling objects, other objects' nets - is not on that
canvas at all: not painted, not hit-testable, not selectable. A new place or transition drawn
there belongs to that object from the moment it appears.

**There is no Save and no Cancel.** An edit made on an object's canvas is an edit to the model
at the moment it is made, exactly like an edit on the net's own canvas; `Ctrl+Z` undoes it and
`File > Save` writes it out. Leaving a canvas - clicking another pill, closing this one,
starting a simulation - asks nothing, because nothing is pending. Starting Run or Animate
switches to the net's own canvas first: a run is a run of the whole model.

### Nested objects

Grouping a selection always groups what is on the active canvas, so grouping inside an object
produces an object **nested** in it. A nested object is created collapsed and is drawn on its
parent's canvas as a summary box - its header, its priority, its eye icon and
`N elements hidden` - with a port on its border per element of everything it holds, so a
crossing connection has somewhere to reach. Its own net is not drawn there and is not
hit-testable; **Expand** on its context menu shows the net inside its parent again, **Collapse**
puts the box back. Dragging a parent moves every object nested in it and both their nets.

Removing an object's frame lifts what it held one level out: its elements to the object that
enclosed it, or to the free elements when nothing did, and the objects nested in it onto that
same enclosing object. Its canvas closes with it, and the active canvas falls back to the
nearest surviving enclosing one.

A nested object is an ordinary sibling `<page>` in an exported PNML document and imports back
flat, which is what the web editor does with the same relation.

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
| `ua.stetsenkoinna.graphpresentation.CanvasSelection` | The one selection store, holding both elements and object frames, so an operation is written once |
| `ua.stetsenkoinna.graphpresentation.objmodel.CanvasStack` | Which canvases are open and which is active; a canvas is a level of the one document, not a second one |
| `ua.stetsenkoinna.graphpresentation.objmodel.CanvasTabsBar` | The strip of pills along the bottom of the canvas area |
| `ua.stetsenkoinna.graphpresentation.objmodel.NetTemplateDialog` | Instantiating a net library template with arguments |

Run and Animate simulate the whole canvas, animation included — a token leaving one object is
seen arriving in another. A canvas without frames is a model of one object, so plain net
editing is unchanged.

A composed model is saved and loaded as **PNML**: the native `.pns` format writes the bare net,
so object frames, nesting and shared places survive only through PNML import/export.

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
