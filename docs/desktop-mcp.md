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
The generated configuration includes the current port and token. After stopping and
starting the server, copy the updated configuration because the token changes.

## Tools

`inspect_ui` reads application windows, controls, menus, current values and component IDs.
`screenshot` captures a visible application window. `ui_action` operates controls,
key bindings, file dialogs and canvas mouse gestures through existing Swing handlers.
Actions run on the event dispatch thread. Inspect again after each action to see its
result or a newly opened dialog. IDs expire at the next inspection. Native file dialogs
accept a path through the `file` action; existing-file saves ask for confirmation.

The server is initially stopped, binds only to `127.0.0.1`, requires a bearer token,
and rejects browser-originated requests. Closing the MCP window leaves it running;
**Stop server** or closing the editor disconnects clients.

## Implementation

### Shared modeling skills

`resources/list` exposes the canonical guides from
[petri-modeling-skills](https://github.com/sergiorbk/petri-modeling-skills).
Read them with `resources/read` at `petri://skills/petri-modeling`,
`petri://skills/petri-patterns`, `petri://skills/pnml-standard` and
`petri://skills/pattern-catalog`. `prompts/get` with name `build_petri_model`
and argument `description` combines those guides with the Swing tool adapter.
The guides describe modeling semantics; they do not add web-only tools to the editor.

The submodule at `petri-swing-ui/vendor/petri-modeling-skills` pins the source revision.
Run `git submodule update --init --recursive` before building. Maven packages the
canonical Markdown files, manifest and license into the JAR. To upgrade, check out
the desired shared revision in the submodule, run the tests and commit the changed
submodule pointer. Edit shared rules in the shared repository rather than copying
them into this application.

### Desktop actions

Annotate a service method with `@McpTool`. Its declaration supplies the name,
description and argument schema for both discovery and invocation. The HTTP transport
uses `McpToolRegistry`; it has no separate list of tool implementations. The UI bridge
calls existing controls and listeners rather than reimplementing model operations.

References: [Claude Code MCP](https://code.claude.com/docs/en/mcp),
[Claude Desktop local configuration](https://modelcontextprotocol.io/docs/develop/connect-local-servers),
[Codex MCP configuration](https://learn.chatgpt.com/docs/extend/mcp).
