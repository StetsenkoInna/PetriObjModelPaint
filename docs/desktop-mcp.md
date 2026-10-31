# Desktop MCP

Open **AI > MCP**, select **Start server**, and keep the editor running.
The window has short setup tabs for **Codex**, **Claude Code**, and **Claude Desktop**,
plus buttons to copy the URL, token, or selected client setup.

## Connect a client

- **Codex:** copy the TOML entry from the Codex tab into `~/.codex/config.toml`, then
  reconnect or restart the client. The same configuration serves desktop, CLI and IDE.
- **Claude Code:** run the command from its tab, then check `/mcp`.
- **Claude Desktop:** open **Settings > Developer > Edit Config**, merge the generated
  entry into `claude_desktop_config.json`, then restart Claude Desktop. The entry uses
  the application's Java runtime and bundled stdio bridge. No Node.js package is needed.

Codex and Claude Code connect directly over Streamable HTTP. Claude Desktop's local
configuration starts a small stdio process that forwards MCP messages to the same
running editor. Claude's cloud connectors cannot reach this localhost endpoint.
Token authentication is optional and **off by default**. To enable it, stop the server,
select **Require token**, and start it again. The generated client setup includes an
authorization header (or bridge token) only when authentication is enabled. Copy the
updated setup after changing the mode; remove old authentication entries when disabling it.
The **Copy token** button is available only while authentication is enabled.
The authentication choice is saved separately; an older saved token alone does not
enable authentication.

The generated configuration includes the saved port and optional token. Enabling MCP remembers
the connection settings in the application's per-user `ui.properties` and starts the server automatically
after application restart. Existing client configurations continue to work. Explicitly
stopping the server disables automatic startup and retains the connection settings
for the next manual start.

## Tools

### Direct model API

Use the direct tools for building, editing and navigating models. Creating objects,
connecting ports, inserting library nets and improving spacing do not require mouse
gestures or property dialogs. Start with `get_model_capabilities` for the supported
workflow and connection rules, then read `get_model`.

| Tool | Behavior |
| --- | --- |
| `get_model_capabilities` | Discover the direct workflow, model fields and connection semantics |
| `get_model` | Complete structured document, stable IDs, derived ports and revision; optional full PNML |
| `apply_model` | Atomically build or replace a complete flat or composed document from JSON |
| `edit_model` | Atomically update nodes, arcs, objects, nesting, groups, links and parameters by ID |
| `undo_model` | Undo one edit, or redo with `redo: true`, using a revision guard |
| `set_model_view` | Set world camera coordinates and zoom, fit the drawing or focus an object |
| `optimize_model_layout` | Improve spacing or preview it, with geometry preservation and residual diagnostics |
| `list_model_templates` | List library methods with typed arguments and saved palette templates |
| `insert_model_template` | Instantiate a library method or saved template directly as an object |
| `export_model_fragment` | Save selected object subtrees as a reusable composed PNML module |
| `insert_model_fragment` | Insert a composed PNML module with new prefixed IDs |
| `validate_model` | Check a copy with the application's PNML and simulation builders |
| `simulate_model` | Execute a fresh copy and return status, markings, statistics, trajectory and protocol |
| `open_model` | Load PNML text or an absolute file path, including Petri-objects and links |
| `save_model` | Write PNML directly, with explicit overwrite protection |

Read `get_model` before changes. Pass its `revision` as `expected_revision` to
`apply_model`, `edit_model` or `open_model`. A stale revision fails rather than
overwriting another client's edit. Apply/open require `replace_current=true` when
the canvas is nonempty. A batch is prepared before committing on the Swing EDT;
an invalid batch leaves the drawing unchanged. Use `undo_model` with the current
`expected_revision` to undo a change, or add `redo: true` to redo it. Stop an
interactive simulation before editing. Direct simulations leave canvas markings
unchanged and have the existing four-second/10000-event execution limits.

For example, `apply_model` accepts the following `model` (along with the revision):

```json
{"name":"Test","places":[{"id":"p1","tokens":1,"x":160,"y":220},
 {"id":"p2","tokens":0,"x":520,"y":220}],
 "transitions":[{"id":"t1","delay":2,"x":340,"y":220}],
 "arcs":[{"id":"a1","source":"p1","target":"t1"},
 {"id":"a2","source":"t1","target":"p2"}]}
```

Then `simulate_model` with `horizon: 10` observes p1=0 and p2=1 on the copy.
Use `edit_model` with `operations: [{"op":"put_place","id":"p1","tokens":3}]`
to change a marking. Puts create new elements or update supplied fields; a new
node needs its coordinates and marking/delay. `remove` also removes incident arcs.
IDs are unique ASCII XML NCNames and positions are node centers. Coordinates can
be negative; the world origin starts at the center of a new canvas. Time values share the
model clock; no unit conversion occurs. Distribution defaults to deterministic.
For `unif`, deviation means half-range; for `norm`, standard deviation. `read: true`
is an availability test on an input arc, not an inhibitor zero-test.

The full `model` document has `name`, `places`, `transitions`, `arcs`, `objects`,
`links` and `groups`. `get_model` also returns a `ports` array outside `model`.
Pass the returned `model` to `apply_model`; ports are derived again from ownership.
`include_pnml: true` adds the complete PNML representation for interchange.

#### Objects, ports and connections

Create an object with `objects[]` or `put_object`, giving it an `id`, `name`,
`x`, `y`, `width`, `height` and `members` containing node IDs. Minimum frame size
is 120 by 80. Use `parent` for nesting and `groups[].objects` for replication
groups. An element has one direct owner. Objects support `priority`, `collapsed`,
`content_visible` and a `template` with `method` and string `arguments`.
Groups also retain their template method and arguments.

Ports are addresses of existing nodes, written `objectId:elementId`. They are
not extra places or transitions. `get_model.ports` includes their kinds and
perimeter positions, including descendant ports exposed by an enclosing object.
Links accept a valid port address or a node ID. The returned document uses
canonical owner addresses and stable derived link IDs. Omit `id` for a new link.

Use these connection types in `links[]` or `connect_ports`:

- `placeFusion`: source is the master place, target is a local alias in another
  object. Both reference one marking. This shares a queue or resource; it neither
  transfers nor duplicates tokens. Editing an alias marking updates the master
  and the other aliases.
- `transitionToPlace`: source is a transition, target is a place in another
  object. The transition delivers `weight` tokens to that place when it completes.

Ordinary and read arcs in `arcs[]` must stay inside one object, or between unowned
nodes. To consume from or test an external queue, create a local place, fuse it
to the master queue, and draw the input or read arc inside the consuming object.
Cross-object place-to-transition arcs are rejected rather than silently reinterpreted.
For example, a worker can test a shared resource and receive work from a producer:

```json
{"links":[
  {"type":"placeFusion","source":"pool:resource","target":"worker:localResource"},
  {"type":"transitionToPlace","source":"producer:send","target":"worker:input","weight":1}
],"arcs":[
  {"id":"a_resource","source":"localResource","target":"serve","read":true},
  {"id":"a_input","source":"input","target":"serve"}
]}
```

This is a document fragment; its nodes and objects must already exist or be
included in the complete model. Optional `boundary_stub: {"x": 12, "y": -8}`
on arcs and links preserves boundary connection offsets through save/open.

`edit_model.operations` supports `put_place`, `put_transition`, `put_arc`,
`put_object`, `put_group`, `connect_ports`, `disconnect_ports`, `set_owner`,
`move_object`, `replicate_object`, `remove` and `rename_model`.
`set_owner` takes the destination object `id` and node `members`; `id: "free"`
releases nodes from ownership. It still must produce valid local arcs and links.
`move_object` translates its subtree by `dx` and `dy`; `put_object` changes the
frame itself. Object removal ungroups by default, promoting members and nested
objects to its parent. A delivery link that becomes local is converted to an
ordinary output arc. An operation that would create an unsupported internal
place fusion fails atomically. `cascade: true` deletes the subtree instead.

`replicate_object` takes an object `id`, a unique `prefix`, `count` and optional
`dx`/`dy`. It copies the subtree, relative arrangement and internal links with
new IDs. External connections are omitted by default. With
`include_external_links: true`, an outside master resource can also be linked
to each copied local alias. `group_id` creates a replication group; use
`include_original: true` to include the source root in that group. Existing
group membership rules still apply.

#### Symbolic values and reusable modules

Symbolic fields are `tokens_parameter` on places; `delay_parameter`,
`distribution_parameter`, `priority_parameter`, `probability_parameter` on
transitions; and `weight_parameter`, `read_parameter` on input arcs.
Output arcs and delivery links support `weight_parameter`. Set a numeric or
literal value in a put to clear its corresponding parameter, or supply an empty
parameter name. Numeric `connect_ports.weight` also resolves a delivery quantity.
Unresolved symbolic cross-object transfer weights are rejected for export and
simulation rather than replaced by their numeric placeholders.

Use `list_model_templates` before `insert_model_template`. Insert accepts exactly
one of `method` or `template_id`, string `arguments`, an ID `prefix`, world
`x`/`y` and `expected_revision`. Arguments are converted to the library method's
declared types. Saved prototypes accept no library arguments. The response
includes inserted object IDs and the updated document and ports.

`export_model_fragment` takes `object_ids`, an absolute `path` and
`expected_revision`. It keeps internal port connections, parameters, nesting
and complete groups. External links are omitted and counted in
`omitted_external_links`. Use `overwrite: true` explicitly to replace a file.
`insert_model_fragment` takes an absolute PNML `path`, unique `prefix`, world
center `x`/`y` and `expected_revision`. It preserves the module's relative
arrangement and internal composition. Neither fragment tool changes the
editor's current Save path; insertion supports Undo.

#### Camera and spacing

`set_model_view` changes the camera without changing the model. Use `center_x`,
`center_y` and `scale`, `fit: true`, or `object_id` to focus an object's canvas.
`object_id: "root"` returns to the outer canvas. Focusing an object preserves its
saved collapsed state. Signed world coordinates work on every side of the origin.

`optimize_model_layout` requires `expected_revision`; optional `minimum_gap`
defaults to 24. `apply: false` previews a report without committing changes.
Applying supports Undo and fits the result unless `fit: false` is supplied.
The optimizer changes geometry while preserving IDs, topology, markings,
timing, parameters, ownership, nesting, groups and port semantics. It uses
rendered label bounds, adjusts distances and expands frames where needed.
It retains the existing arrangement where possible and does not rebuild the net.

Inspect `report.verified`, `verification_scope`, `after_issue_count` and
`diagnostics` before treating spacing as complete. Diagnostics identify affected
elements and include labels, weights, headers, group titles and hidden or
collapsed port labels at the canvas font. Straight arrows are retained;
remaining crossings are reported rather than rerouted. A successful tool call
or `semantic_preserved: true` does not imply that every readability issue was
resolved. The same action is available from the left toolbar below the object
tool and **View > Improve spacing**.

Validation reports engine compatibility, not RELAX NG conformance or proof that
a model implements a user's task. Simulations return their actual completion
status; a time or event limit is not reported as successful completion.

### UI API

These compatibility tools remain available for application controls and visual
inspection. Prefer the direct model tools for supported modeling operations.

`inspect_ui` reads application windows, controls, menus, current values and component IDs.
`screenshot` captures a visible application window. `ui_action` operates controls,
key bindings, file dialogs and canvas mouse gestures through existing Swing handlers.
Actions run on the event dispatch thread. Inspect again after each action to see its
result or a newly opened dialog. IDs expire at the next inspection. Native file dialogs
accept a path through the `file` action; existing-file saves ask for confirmation.

MCP is disabled on a fresh installation. It binds only to `127.0.0.1` and rejects
browser-originated requests in both authentication modes. Without **Require token**,
local clients can connect without credentials. Closing the MCP window leaves
it running. Closing the editor disconnects clients without disabling automatic startup;
**Stop server** disables it. If the saved port is unavailable, the editor still opens
and the MCP window reports the startup failure so the port can be corrected.

## Implementation

### Shared modeling skills

`resources/list` exposes the canonical guides from
[petri-modeling-skills](https://github.com/sergiorbk/petri-modeling-skills).
Read them with `resources/read` at `petri://skills/petri-modeling`,
`petri://skills/petri-patterns`, `petri://skills/pnml-standard` and
`petri://skills/pattern-catalog`. `prompts/get` with name `build_petri_model`
and argument `description` combines those guides with the Swing tool adapter.
The guides describe modeling semantics; they do not add web-only tools to the editor.

Maven downloads the versioned resource ZIP from GitHub Releases during
`generate-resources`, verifies its pinned SHA-256 and packages the Markdown,
schemas, manifest and license into the JAR. A plain clone and the usual Maven
build command are sufficient; no submodule, token or Python runtime is needed.
The archive is cached in the local Maven repository and checked again on reuse.
To upgrade, update `petri.skills.version` and `petri.skills.sha256` in the desktop
POM after publishing the reviewed assets. Edit shared rules in the shared
repository rather than copying them into this application.

### Desktop actions

Annotate a service method with `@McpTool`. Its declaration supplies the name,
description and argument schema for both discovery and invocation. The HTTP transport
uses `McpToolRegistry`; it has no separate list of tool implementations. The UI bridge
calls existing controls and listeners rather than reimplementing model operations.

References: [Claude Code MCP](https://code.claude.com/docs/en/mcp),
[Claude Desktop local configuration](https://modelcontextprotocol.io/docs/develop/connect-local-servers),
[Codex MCP configuration](https://learn.chatgpt.com/docs/extend/mcp).
