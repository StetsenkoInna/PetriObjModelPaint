# Training bundles and network images

Use **File > Export training data...** to save a local ZIP named from the title and
UTC export time. Enter the task and select a human review status. The default is
`unreviewed`; successfully writing a ZIP does not certify model correctness.

The bundle follows the shared
[training bundle contract](https://github.com/sergiorbk/petri-modeling-skills):

- `model.pnml`: the current document, using the same serializer as ordinary PNML save.
- `task.json`: supplied task, clarifications, assumptions and acceptance criteria.
- `conversation.jsonl`: real user/assistant messages supplied by the MCP client.
- `tool-calls.jsonl`: recorded calls when available, otherwise an empty file and an
  explicit missing-data reason. The desktop cannot recover an external client's history.
- `verification.json`: unknown checks and simulation status unless structured evidence
  has actually been recorded. Raw editor panels are not reinterpreted as passed checks.
- `network.png` and `model-geometry.json`: the current active-canvas image, source shape
  coordinates and the image transform. Current collapsed/focused visibility is preserved.
- `editor-observations.json`: current controls, potentially stale protocol/statistics
  text, source-node identifiers and viewport metadata.
- Optional `screenshot.png`: the main-window QA view, excluding owned connection dialogs.

`manifest.json` lists the application and shared-skills versions, capture scope,
missing observations and a SHA256 for every other artifact. Unknown source revisions
remain null. No conversation, tool result or successful validation is invented.

The geometry is marked `unverified`. Its body bounds can include elements hidden by
the current canvas view and are not reviewed detection labels. The image transform is
`pixel = (model_coordinate - origin) * scale`; source canvas IDs and model IDs are
reported in the editor observations. QA screenshots have their own frame-relative
coordinate system and must not be confused with the network image.

## MCP export

`export_training_data` requires `title` and `output_directory`. Optional fields are
`task`, string arrays `clarifications`, `assumptions`, `acceptance`, review `status`,
`include_screenshot`, and an actual `conversation` array of `{role, content, timestamp?}`
messages. Roles are `user` or `assistant`; supplied timestamps use UTC. The result gives
the local ZIP path, hash, artifact count and review status. Existing archives are kept.

## Standalone PNG

Use **File > Export image...** or MCP `export_network_image` with `output_path` ending
in `.png`. The MCP tool preserves an existing file unless `overwrite` is explicitly
true. The same renderer supplies standalone PNGs and training-bundle `network.png`.
It reuses the canvas painter, includes labels/arcs/object frames, and omits window
chrome. Large images are scaled to bounded dimensions. PNG text metadata records the
origin, scale, dimensions and active-canvas scope.
