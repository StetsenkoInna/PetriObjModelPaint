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

Agents can work with the model without mouse gestures or property dialogs:

| Tool | Behavior |
| --- | --- |
| `get_model` | Stable model IDs, markings, timing, layout and revision; optional full PNML |
| `apply_model` | Atomically build a complete flat net from JSON |
| `edit_model` | Atomically create, partially update or remove elements by ID |
| `validate_model` | Check a copy with the application's PNML and simulation builders |
| `simulate_model` | Execute a fresh copy and return status, markings, statistics, trajectory and protocol |
| `open_model` | Load PNML text or an absolute file path, including Petri-objects and links |
| `save_model` | Write PNML directly, with explicit overwrite protection |

Read `get_model` before changes. Pass its `revision` as `expected_revision` to
`apply_model`, `edit_model` or `open_model`. A stale revision fails rather than
overwriting another client's edit. Apply/open require `replace_current=true` when
the canvas is nonempty. A batch is prepared before committing on the Swing EDT;
an invalid batch leaves the drawing unchanged. Changes support Undo. Stop an
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
IDs are unique XML NCNames and positions are node centers. Time values share the
model clock; no unit conversion occurs. Distribution defaults to deterministic.
For `unif`, deviation means half-range; for `norm`, standard deviation. `read: true`
is an availability test on an input arc, not an inhibitor zero-test.

Structured editing is deliberately limited to flat concrete nets. Use
`get_model(include_pnml=true)` and `open_model` to preserve composed objects, links,
templates and symbolic parameters. Validation reports engine compatibility, not
RELAX NG conformance or proof that a model implements a user's task.

### UI API

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
