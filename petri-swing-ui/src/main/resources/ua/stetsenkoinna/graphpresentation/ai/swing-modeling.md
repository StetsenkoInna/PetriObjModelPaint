# Direct desktop model API

Start with `get_model_capabilities` and `get_model`. Model construction uses direct
tools, including composed models. Do not use `inspect_ui` or `ui_action` to create,
group, nest, duplicate or connect model elements. UI controls are for inspecting
appearance, testing the interface or handling a specifically requested UI task.

`apply_model` accepts the complete document: name, places, transitions, local arcs,
objects, links and groups. `edit_model` applies an atomic batch to the same document.
No drawing gesture or property dialog is necessary. Use the revision from the last
read as `expected_revision`; stale or invalid batches leave the model unchanged.
Apply/open need replace_current=true for a nonempty document. `undo_model` restores
an edit or redoes it without clicking controls. Data is scoped to the complete document,
regardless of which object's canvas happens to be visible.

## Objects and ports

Create an object directly in objects[] or with put_object. Its members[] explicitly
lists the place and transition IDs it owns. Coordinates do not decide ownership.
Use parent for nesting. `set_owner` moves membership; `move_object` translates the
whole subtree, while put_object can change only the frame bounds. replicate_object
copies a subtree with fresh prefixed IDs and its internal connections. External
connections are copied only when include_external_links=true. A canonical resource
outside a replicated object can be shared by all copies through their local aliases.

Ports are derived from object members. `get_model.ports` returns IDs in the form
objectId:elementId, kind and perimeter position. Do not create fake port nodes.
Nested-object boundary ports can address elements in the object's subtree.

- `placeFusion`: source is the canonical place, target is its local alias in another
  object. They share ONE marking. Seed the canonical resource once; do not add the
  alias counts as extra capacity. One source may have several aliases.
- `transitionToPlace`: a transition delivers weight tokens to a place in another
  object. This is a special output connection, not shared identity.
- Ordinary and read arcs stay inside one object. To consume from an external queue,
  create a local place in the consuming object, fuse it to the canonical queue,
  and draw an ordinary input arc from that local place to the local transition.
  A read arc tests availability without consuming; it is not an inhibitor zero-test.

For example, two object fragments can have:

```json
{"links":[
  {"type":"placeFusion","source":"pool:resource","target":"machine:local_resource"},
  {"type":"transitionToPlace","source":"source:finish","target":"machine:queue"}
]}
```

Use links[] in apply_model or connect_ports/disconnect_ports in edit_model. Link IDs
are derived and returned; omit id when creating a new connection. A fusion has no
transfer weight. Direct cross-object place-to-transition and transition-to-transition
connections are invalid. Do not substitute an informational arc for a shared queue.
Connections between aliases in a single runtime object are rejected rather than
silently changing their meaning.

## Parameters and reusable modules

Symbolic values are preserved by tokens_parameter, delay_parameter,
distribution_parameter, priority_parameter, probability_parameter, weight_parameter
and read_parameter. Supplying a concrete numeric/literal field clears its symbolic
field. Editing a shared place updates all aliases. Resolve symbolic cross-object
output weights before PNML export or simulation; the engine link contract requires
a concrete quantity. Do not interpret placeholder numeric values as resolved symbols.

`list_model_templates` exposes library methods, typed arguments and saved palette
templates. `insert_model_template` instantiates one at signed world coordinates.
`export_model_fragment` saves selected object subtrees with internal links, nesting,
groups and parameters. `insert_model_fragment` inserts such a module with fresh
prefixed IDs, preserving its relative arrangement. Use these tools instead of
opening the Nets window, saving objects through dialogs or stamping palette tools.

## Geometry, checks and saving

Coordinates are world coordinates and can be negative. A new canvas centers (0,0)
and can pan in all directions. `set_model_view` focuses an object/root, sets camera
center/zoom or fits the drawing, without moving the model. Save/open preserve world
coordinates. Node coordinates are centers; object coordinates are frame corners.

`optimize_model_layout` improves spacing without rebuilding topology or changing
parameters. It is optional, revision checked and undoable. Inspect its report:
verified=true only covers the stated verification_scope, readability_verified
separates unreadable labels from remaining crossings. Do not claim an arbitrary
complex diagram is clear when diagnostics remain. Use apply=false for a preview.
Do not automatically apply it as a final step unless its evidence justifies doing so.

`validate_model` checks engine compatibility; it is not proof of task correctness.
`simulate_model` runs a fresh copy and returns observed states/statistics/trajectories,
leaving canvas markings unchanged. It reports limits and failures explicitly.
Delays and horizon use the same model clock; changing UI time-unit labels does not
convert values. Uniform deviation is half-range; normal deviation is standard deviation.

`save_model` writes the full PNML document to an absolute path, preserving existing
files unless overwrite=true. `open_model` reads full PNML directly, including legacy
models. PNML editing is an interchange option, not a required workaround for objects.
Use export_network_image for a real renderer image. Dataset/review tools preserve
actual task, conversation, checks and version history; follow their shared guides.
Never invent conversation messages or claim that ZIP export itself proves correctness.
