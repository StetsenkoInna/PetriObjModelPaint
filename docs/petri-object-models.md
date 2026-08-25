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

Two kinds of link connect them:

| Link | Written | What happens |
|------|---------|--------------|
| **Shared place** | `O0.P2 = O1.P1` | The two places become one instance. Both objects read and change the same marking — the classic composition of the technique. One place may be repeated by any number of others; see [One source, many copies](#one-source-many-copies). |
| **Transition → place** | `O1.T1 → O2.P1 ×k` | Whenever the transition fires, it delivers `k` tokens into a place of the other object, without owning an output place of its own. |

A transition's firing condition is always made of input places drawn in its own net; there is
no link that adds a foreign place as an extra input of a transition. To make a place of one
object feed a transition of another, fuse that place with a place already sitting in the
consuming object's net, one that is wired to the target transition by an ordinary input arc
drawn inside that object's own editor: the fused identity plus the ordinary arc is what
carries tokens across the object boundary. This is the same shared-place composition above,
just aimed at a transition instead of left as a bare shared place.

An earlier version of the technique had a third kind of link, "place → transition" (with an
"informational" variant that tested a place without consuming it), that let a transition carry
a second, foreign set of input places directly — exactly what the paragraph above replaces with
plain fusion. A document whose links still declare that `placeToTransition` kind is rejected on
import: the parser reports an error rather than reinterpreting it, since there is no longer a
link type for it to become.

Links are declarations, not wiring: a model stores what is connected to what by index, and
applies it when the objects are built. That is what lets a model be cloned, saved to a file,
and rebuilt on a server from the same description.

---

## Composing a model in the editor

A Petri-object is a named frame on the canvas the nets are drawn on. Whatever is drawn
inside it belongs to it, so the structure of a model and the behaviour of its objects are one
picture. Every object action — creating, naming, linking, deleting — is reached by
**right-clicking** whatever it applies to; there is no separate menu for it.

**Making objects.** Right-click gives you the action that fits what is under the pointer:

- select some elements first, then right-click any of them (or the empty canvas) for
  **Group selection into Petri-object** — the way an existing net is split into objects
- right-click empty canvas for **New empty Petri-object** or **Petri-object from net
  library...**, which instantiates a template with arguments of its own —
  `CreateNetSMOwithoutQueue(2, 0.5, "First")` — and frames the result
- right-click an existing frame for **Duplicate Petri-object**, the quick way to a model of
  several alike objects

**Handling frames.** Grab a frame by its header to move it — its net travels with it, so an
object's elements always stay inside its frame — by its bottom-right corner to resize it, which
does not move them; either way an element's own position only ever changes one at a time inside
the object's own editor. The small eye icon in the header shows or hides the object's
own drawing without changing the frame's size at all — a purely visual choice, for a model with
more objects than screen space; the object's places and transitions still exist and still hold
their marking either way, they are just not painted while the eye is closed. `Ctrl` below means
`Command` on macOS, as in the [desktop UI guide](desktop-ui.md#keyboard-shortcuts). `Ctrl+A` selects
every frame along with every place and transition; `Delete` removes whatever frames are
selected (their nets stay on the canvas), `Ctrl+D` duplicates the current one — the same
actions the elements on the canvas already respond to, extended to objects too. Rename,
priority and removal are also on a frame's right-click menu. Right-clicking a place or
transition, unlike everywhere else on the canvas, does nothing on its own — it neither selects
nor opens anything; double-click it instead.

**Editing an object's net.** Once elements belong to a frame they are locked on the shared
canvas — position, arcs and everything else about them can only be changed by opening the
object's own editor: **double-click anywhere on the frame**, or right-click it and choose
**Edit net...**. That opens an ordinary net editor scoped to just this object, operating on the
very same places, transitions and arcs the main canvas holds for it. **Save** is what applies
whatever changed there — additions, removals, anything moved — back onto the canvas, and also
refits the frame's outline around wherever its contents ended up; **Cancel**, or closing the
window any other way, restores every element to the position it had when the editor opened and
applies nothing else. A frame without anything inside is empty until you open it and draw.

**Linking objects.** A locked object still reaches the outside world — dragging from one of its
own places or transitions works exactly like dragging from a free element's, whether or not the
eye currently has that object's content shown. While it is shown, drag straight from the real
place or transition; while it is hidden, drag from its **port** instead — a small labelled
circle standing in for it, sitting on whichever of the frame's left, right or bottom sides is
nearest to that element's own position inside the object, so a port reads as roughly where its
element actually is rather than an arbitrary slot; the top side, under the header, never carries
one, and a port is only drawn while there is nothing else on screen for it to stand in for.
Dragging makes the link that fits what it is dropped on — a port, a free place or transition, or
another object's own element while that one is shown too:

| Drag from a place (or its port) to | What you get |
|-------------------------------------|---------------|
| another place (or its port) | the second place becomes a copy of the first — the two are one instance |

The drag runs **from the source to the copy**, and the direction is kept: the place you started
from is the one whose marking the other repeats.

| Drag from a transition (or its port) to | What you get |
|-------------------------------------------|---------------|
| a place (or its port) | the transition delivers tokens into that place |

There is no drag gesture from a place onto a foreign transition. To feed a transition of
another object from a place, drag that place onto the place already feeding the target
transition inside the consuming object's own net, fusing the two — the ordinary arc from the
fused place into the transition is what was drawn there when that object's net was made.

The line drawn for a link is an ordinary arc's — the same border-trimmed line and arrowhead a
free element's arc gets, not a raw line pointing at bare centres — anchored to whichever of the
two ends actually has something on screen to trim against: the real element while it is shown,
its port while it is hidden. A weight for a transition-to-place link is set the same way an
ordinary arc's is, by double-clicking it. Free elements — anything outside
every frame — stay directly draggable and connectable exactly as before Petri-object composition
existed, on top of being reachable from a port; a transition still cannot connect directly to
another transition, framed or free.

**Running.** **Run** and **Animate** simulate the whole canvas: every frame is an object,
every port-to-port link is a link between them, and anything outside every frame is one more
object. Animation plays on the same canvas, so a token leaving one object is seen arriving in
another. A canvas without frames is a model of one object and behaves exactly as a plain net
always did.

While animating, the object whose transition is currently firing has its frame's border lit up,
so which object is doing something is legible without having to watch individual places and
transitions for it. A link crossing to another object — or to a free element, which has no
frame of its own to light up but still gets its own brief pulse — lights up both ends of the
crossing together, in a second colour, so a token moving between objects is as easy to follow as
one moving within a single one.

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

### A hierarchy of objects

An object nested inside another has its `<page>` written **inside** its parent's page, which
is how ISO/IEC 15909-2 expresses a hierarchy of pages. Only top-level objects, and the page
holding the elements that belong to no object, sit directly under `<net>`. A child page comes
after everything its parent page holds of its own, and several children of one parent are
written in ascending object index.

```xml
<page id="object0">
  <name><text>Outer</text></name>
  <toolspecific tool="PetriObjModel" version="2.2.2">
    <petriObject index="0" name="Outer" priority="0" x="40" y="40" width="600" height="400"/>
  </toolspecific>
  <place id="p0"> ... </place>
  <transition id="t0"> ... </transition>
  <arc id="a0" source="p0" target="t0"> ... </arc>

  <page id="object1">
    <name><text>Inner</text></name>
    <toolspecific tool="PetriObjModel" version="2.2.2">
      <petriObject index="1" name="Inner" priority="0" x="80" y="140" width="300" height="200"/>
    </toolspecific>
    ...
  </page>
</page>
```

A page's own net is what that page itself holds: the places, transitions and arcs of a nested
page belong to the nested object alone, and an arc never crosses from one page into another.
Objects are still addressed by the `index` its `petriObject` element states, links included,
so nesting changes where a page is written and nothing about what it means.

The page structure is the only statement of the hierarchy this code knows: the reader takes
every object's parent from the page that encloses it and from nothing else. Documents written
before the pages were nested carry the hierarchy in a tool-specific `parentObject` attribute
instead, with every page a flat sibling. That attribute is neither written nor read any more,
so such a document still opens, with its objects, their nets and their links intact, but with
the nest lost: nothing encloses any of its pages, so every object arrives at the top level.

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
model.go(1_000_000);
```

`linkObjectsCombiningPlaces` is also how a place of one object is made to feed a transition of
another: the server's queue place (`otherPlace`, index 0) is already wired by an ordinary arc
to the server's own service transition inside `CreateNetSMOwithoutQueue`'s net, so fusing the
generator's output place into it is what carries the generator's tokens into that transition.
There is no separate link for "place feeds a foreign transition" — it is this same fusion,
aimed at a place an object's net already consumes from.

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

## One source, many copies

A place may be repeated by any number of other places. Drag from the source onto each place that
should repeat it, one link at a time; there is no separate gesture for a fan.

The model keeps one pairwise link per copy rather than a single one-to-many object, because that
is what the file format keeps too. PNML has no node meaning "shared with many": a
`<referencePlace>` carries exactly one `ref`. What one-to-many is, in PNML, is several reference
places naming the same target — which the standard's own grammar accepts, since its rules for a
reference place are only that `ref` names a place or another reference place, that it does not
name its own element, and that it does not close a cycle. `PnmlRngConformanceTest` validates a
fan-out document against that grammar rather than taking anyone's word for it.

Four things are refused, each for its own reason:

| Refused | Why |
|---|---|
| Linking a place to itself | there is nothing to repeat |
| Linking the same two places twice, or back the other way | these links are one-way; the reverse would make each place repeat the other |
| A place copying two different sources | it would have no answer to whose marking it holds |
| A chain of links closing into a loop | the same, spread over several places — and PNML forbids a cycle of reference places outright |

Chains themselves are fine: a place that copies another may in turn be copied.

Two places that belong to **no** object may now be linked. Two places of the **same** object may
not — an object repeating itself says nothing.

### A linked place is drawn filled

A place that takes part in a link is drawn with a grey interior instead of the plain element
fill — both ends, since once two places are one instance neither one's marking is its own any
more. It says so where the place stands, which matters most on a canvas that does not draw the
other end of the link at all.

### Telling a reference link from an informational arc

Both are thin dashed lines, and they used to differ only in dash length and in whether the
arrowhead was filled. Those are differences of degree, and they are the first thing to disappear
when the canvas is zoomed out, printed or screenshotted.

| | Line | End |
|---|---|---|
| Informational arc | even dash | filled arrowhead at the transition |
| Reference link | dash-dot | filled **node** on the source, no arrowhead |

The reference link has no arrowhead on purpose. An arrowhead promises a flow, and nothing flows
along a reference link — the copy simply *is* the source. Direction still matters and is still
visible, because the node sits on one end only: the end everything else is copying. Where one
source is repeated by several places, their nodes coincide, so the fan reads as one origin
rather than as several unrelated links.

---

## Things to know

- **Element indices are positions.** A link addresses the n-th place or transition of an
  object. Reordering the elements of a net reorders what its links point at; the editor
  rebuilds the net from its drawing before every run and every save, so the two stay in step.
- **A shared place does not move either half.** The two places may sit deep inside two
  different objects, one inside an object and one nowhere in particular, or both nowhere at all;
  joining them never repositions either one, and the connection is always drawn as a line — to a
  port for a half whose object has its content hidden, to the place itself otherwise. There used
  to be a second form for two places belonging to no object, which stacked one on top of the
  other and drew a ring around the pair. It is gone: with one place repeated by several, it
  would have piled all the copies onto one point.
- **A transition's inputs are always local.** A transition-to-place link only ever adds an
  output; the only way a foreign place reaches a transition's firing condition is by being
  fused into a place already drawn as that transition's input inside its own object's net.
- **An object's membership is exactly what put something in it.** Grouping, drawing it inside
  the object's own editor, instantiating it from the net library, duplicating an object, loading
  it from a file, or a confirmed drag onto a frame — nothing else changes it, so moving a frame
  across the canvas can never pick up an element it merely ends up on top of.
- **Dragging a frame moves it by however far it actually moved.** Its elements shift by the same
  delta the frame's own bounds just did — measured after `GraphObjectFrame.moveTo` clamps the
  frame to stay on the canvas, not before, so dragging into the top or left edge stops the
  elements exactly where it stops the frame, rather than letting them drift on past it.
- **Fusion order matters.** Fusing A's place with B's place and then B's place with C's makes
  A point at what B held at the time. Declaration order is preserved on save, on load and on
  clone, so a model always rebuilds the same way.
- **Element numbering is global while a net is built.** `PetriP` and `PetriT` hand out numbers
  from static counters that double as indices into a net's own arrays, so the readers reset
  them per object and serialize concurrent parses.
