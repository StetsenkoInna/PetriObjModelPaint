# Petri-object models

A Petri-object model is several Petri nets running as one simulation. Each net is a
**Petri-object**: it has its own places, transitions and event schedule, and it is composed
with the others through **links**. This is the composition step of the Petri-object
simulation technique — behaviour is described by a stochastic multichannel Petri net, and the
model is assembled from objects the way an object-oriented program is assembled from
instances.

The simulation engine has always supported this. What used to be missing was everything
around it: the editor drew one net, the format stored one net, and the API ran one net, so a
composed model could only be written in Java. Now a model is something you draw, save, run
and stream.

---

## Objects and links

A Petri-object carries three things beyond its net:

| Property | Meaning |
|----------|---------|
| name | how the object is labelled in reports and in the structure view |
| priority | who acts first when several objects want to act at the same simulation moment; higher wins, ties are broken at random |
| index | position in the model, written `O0`, `O1`, … — what links and statistic formulas address |

Three kinds of link connect them:

| Link | Written | What happens |
|------|---------|--------------|
| **Shared place** | `O0.P2 = O1.P1` | The two places become one instance. Both objects read and change the same marking — the classic composition of the technique. |
| **Transition → place** | `O1.T1 → O2.P1 ×k` | Whenever the transition fires, it delivers `k` tokens into a place of the other object, without owning an output place of its own. |
| **Place → transition** | `O2.P3 → O1.T1 ×k` | A place of one object becomes an extra input of a transition of another: the transition needs `k` tokens there to fire, and consumes them. |
| **Place → transition, informational** | `O2.P3 ⇢ O1.T1 ×k` | The same, but the marking is only tested, never consumed — a guard on the foreign object's state. |

A transition still needs at least one input place in its own net; links extend a firing
condition, they do not replace it.

Links are declarations, not wiring: a model stores what is connected to what by index, and
applies it when the objects are built. That is what lets a model be cloned, saved to a file,
and rebuilt on a server from the same description.

---

## Composing a model in the editor

A Petri-object is a named frame on the canvas the nets are drawn on. Whatever is drawn
inside it belongs to it, so the structure of a model and the behaviour of its objects are one
picture — and an arc crossing a frame border is a token crossing an object border.

**Making objects**, from the **Model** menu:

- **Group selection into Petri-object** (`Ctrl+G`) draws a frame around the selected
  elements. This is how an existing net is split into objects.
- **New empty Petri-object** puts an empty frame down to draw into.
- **Petri-object from net library...** instantiates a template with arguments of its own —
  `CreateNetSMOwithoutQueue(2, 0.5, "First")` — and frames the result.
- **Duplicate selected Petri-object** (`Ctrl+D`) copies an object with its net, the quick way
  to a model of several alike objects.

**Handling frames.** Grab a frame by its header to move it — its net travels with it — by its
bottom-right corner to resize it, and double-click the header to collapse it to a node when
the model outgrows the screen. Name and priority are on the same menu. Removing a frame
leaves its net on the canvas.

**Linking objects** uses the ordinary arc tool; where the arc ends decides what it means:

| You draw | Between | What you get |
|----------|---------|--------------|
| place → transition | different frames | the place becomes an extra input of that transition |
| transition → place | different frames | the transition delivers tokens into that place |
| place → place | different frames | the two become one shared place |
| anything | inside one frame | an ordinary arc of that object's net |

A stroke from a place to a place used to be discarded as invalid; between two objects it is
now the shared place. Both halves are kept on top of each other and drawn as one circle with
a second ring, because that is what they are. An arc's weight and its informational flag are
edited by double-clicking it, as for any arc.

Because the frame decides who owns an element, dragging a wired element across a border would
rewire the model — so that move asks first and is undone when the answer is no.

**Running.** **Run** and **Animate** simulate the whole canvas: every frame is an object,
crossing arcs are links, and anything outside every frame is one more object. Animation plays
on the same canvas, so a token leaving one object is seen arriving in another. A canvas
without frames is a model of one object and behaves exactly as a plain net always did.

**Storing.** **File → Import PNML** opens a model and lays it out as frames; **Save → Export
PNML** writes the canvas as a model.

---

## Statistics per object

The statistics module addresses an object by its index. A formula may mix objects freely:

```
P_AVG(O2.P2) + P_AVG(O3.P2) - T_AVG(O4.T1)
```

An argument without a prefix belongs to object `O0`. Suggestions and validation follow the
prefix, so typing `O1.` offers the elements of that object.

> Before Petri-object models existed, the object id reported to a statistics collector was a
> global counter of every Petri-object ever created in the JVM, so it depended on how many
> simulations had already run. It is now the object's position in its model. Formulas that
> relied on the old numbering — where the first object of a fresh model was `O1` — address
> it as `O0` now.

---

## How a model is stored

A model is a PNML document using the structuring the format already has: one `<page>` per
Petri-object inside a single `<net>`. Places, transitions and arcs stay exactly where a plain
PNML reader expects them, so a model of one object is an ordinary Petri net file and every
net saved before still opens.

What is specific to the technique lives in `<toolspecific tool="PetriObjModel" version="2.0">`
blocks: the object's own properties on its page, and the list of links at net level.

```xml
<pnml xmlns="http://www.pnml.org/version-2009/grammar/pnml">
  <net id="QueueingSystem" type="http://www.pnml.org/version-2009/grammar/ptnet">
    <name><text>QueueingSystem</text></name>

    <page id="object0">
      <name><text>Generator</text></name>
      <toolspecific tool="PetriObjModel" version="2.0">
        <petriObject index="0" name="Generator" priority="0" x="40" y="60"/>
        <netTemplate method="CreateNetGenerator">
          <argument>2.0</argument>
        </netTemplate>
      </toolspecific>
      <place id="p0"> ... </place>
      <transition id="t0"> ... </transition>
      <arc id="a0" source="p0" target="t0"> ... </arc>
    </page>

    <page id="object1">
      <name><text>Server</text></name>
      <toolspecific tool="PetriObjModel" version="2.0">
        <petriObject index="1" name="Server" priority="3" x="320" y="60"/>
      </toolspecific>
      ...
    </page>

    <toolspecific tool="PetriObjModel" version="2.0">
      <petriObjectLinks>
        <link type="placeFusion"
              sourceObject="0" sourceElement="1" targetObject="1" targetElement="0"/>
        <link type="transitionToPlace"
              sourceObject="1" sourceElement="0" targetObject="0" targetElement="0"
              quantity="2"/>
        <link type="placeToTransition"
              sourceObject="1" sourceElement="1" targetObject="0" targetElement="0"
              quantity="1" informational="true"/>
      </petriObjectLinks>
    </toolspecific>
  </net>
</pnml>
```

`sourceElement` and `targetElement` are positions, not ids: the n-th place or the n-th
transition of the object's page, in document order. `netTemplate` records where an object's
net came from; the net itself is always written in full, so a model opens even when the
library method it was instantiated from is gone.

A document of several pages is a composed model, and the plain net reader refuses it rather
than merging the pages into one net — it says so, and points at the model reader.

---

## Running a model from code

```java
GraphPetriObjModel model = new PnmlModelParser().parse(new File("queueing-system.pnml"));
PetriObjModel simulation = model.createPetriObjModel("run-1");
simulation.setIsProtokol(false);
simulation.go(1_000_000);
```

Or assembled directly, without a document:

```java
ArrayList<PetriSim> objects = new ArrayList<>();
objects.add(new PetriSim(NetLibrary.CreateNetGenerator(2.0)));
objects.add(new PetriSim(NetLibrary.CreateNetSMOwithoutQueue(1, 0.6, "First")));

PetriObjModel model = new PetriObjModel(objects);
model.linkObjectsCombiningPlaces(0, 1, 1, 0);          // generator's output = server's queue
model.linkPlaceToTransition(1, 2, 0, 0, 1, true);      // server's state guards the generator
model.go(1_000_000);
```

The engine classes involved:

| Class | Role |
|-------|------|
| `PetriSim` | one Petri-object: a net plus its event schedule, priority and index |
| `PetriObjModel` | the objects, their links, and the simulation loop over them |
| `PetriObjLink` | one link declaration, addressed by indices |
| `ExternalArc` | how a transition reaches a place of another object |
| `GraphPetriObjModel` | the graph-level model — objects with their drawings, saved as PNML |

---

## Running a model over HTTP

The v2 endpoints of `petri-server` take the same document. See the
[server integration guide](petri-server-integration.md#petri-object-model-api-v2) for the
full reference.

```bash
curl -X POST http://localhost:8080/api/v2/model/parse \
     -H 'Content-Type: application/json' \
     -d '{"modelXml": "<pnml>...</pnml>"}'
```

---

## Things to know

- **Element indices are positions.** A link addresses the n-th place or transition of an
  object. Reordering the elements of a net reorders what its links point at; the editor
  rebuilds the net from its drawing before every run and every save, so the two stay in step.
- **A shared place is the one thing geometry cannot decide.** Both halves are drawn in the
  same spot, so the frame they sit in cannot say which belongs to which object; the fusion
  remembers that from the moment the two were joined.
- **A transition needs a local input place.** External arcs extend the firing condition, so a
  transition fed only by another object is not a valid net.
- **Fusion order matters.** Fusing A's place with B's place and then B's place with C's makes
  A point at what B held at the time. Declaration order is preserved on save, on load and on
  clone, so a model always rebuilds the same way.
- **Element numbering is global while a net is built.** `PetriP` and `PetriT` hand out numbers
  from static counters that double as indices into a net's own arrays, so the readers reset
  them per object and serialize concurrent parses.
