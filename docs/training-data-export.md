# Training bundles, reviews and image export

Use **AI > Export training data...** to save a dataset ZIP. **Review** contains
**Open archive...**, **Review network...** and **Versions and comparison...**.
Another participant can use ordinary **File > Open...** (Ctrl+O), **Review > Open
archive...**, or drop the ZIP onto the project or Welcome window to validate it,
open its PNML and continue the same history. Successful opens also appear in
**Open Recent**. An unrelated or damaged ZIP leaves the current document intact.
Save a new reviewed ZIP and pass it on; earlier ZIPs remain unchanged.

## Contributor profile

Set **Settings > Contributor profile...** once: nickname, organization, study group
and optional contacts (email, messenger or URL). Without a nickname the application
generates and saves a two-word name such as `crazy-frog`. Renaming keeps the local
contributor ID. New exports include these declared fields. Imported ZIPs never
replace the local profile. Nicknames and hashes do not authenticate a person.

## Evidence and history

| Entry | Contents |
|---|---|
| `model.pnml` | Complete document serialized by the normal PNML writer |
| `task.json` | Supplied task, clarifications, assumptions and acceptance criteria |
| `conversation.jsonl` | Actual supplied messages; imported messages are retained |
| `tool-calls.jsonl` | Observed MCP UI/training calls; image responses use hashes instead of duplicate image bytes |
| `verification.json` | Unknown checks/simulation unless actual structured verification is recorded |
| `network.png`, `model-geometry.json` | Complete document with every nested object expanded and visible, with matching unverified geometry |
| `network-views.json` | View IDs, names, parent IDs, zero-based PNML object indices and image/geometry paths |
| `networks/object-NNNN.png`, matching `-geometry.json` | Separate expanded subtree for each object at every depth, including empty objects |
| `editor-observations.json` | Controls, source identifiers and viewport metadata; protocol/statistics text may be stale |
| Optional `screenshot.png` | Actual main-window QA view, excluding owned connection dialogs |
| `dataset-history.json` | Persistent bundle ID, saved head revision, revision chain and review IDs |
| `history/revisions/HASH/` | Preserved snapshot: PNML, images, geometry, context, available transcript, observations and its own manifest |
| `history/reviews/HASH.json` | Explicit review, findings, declared reviewer/type, timestamp, reviewed and result revision IDs |

Rendering uses an independent snapshot preserving IDs and parameter values. It does
not change the editor's focus, collapse/eye state, selection or undo history.
New training images include a parameter table below each diagram: initial tokens,
delay, distribution, deviation, priority, probability, arc weight/informational
status, symbolic parameter names, and object priority/parent. Matching geometry
contains structured `parameters` and `parameter_table_visible`; transient simulation
statistics are not model inputs. Standalone image export offers an **Include model
parameter table** option (`include_parameters` over MCP).
Images
are limited to 8192 pixels per side and approximately 24 megapixels; large drawings
are reduced. The overview preserves the existing layout rather than rearranging it.

`manifest.json` uses schema `1.1`, records contributor/application/shared-skills
provenance, capture scope and missing-data reasons, and hashes every other entry.
Legacy `1.0` snapshots are readable and acquire history on export. Credential-shaped
metadata is redacted; unknown observations remain unknown.

Filenames include revision/review counters, for example
`training-queue-r005-review003-20261030T120000000Z.zip`. Internal bundle IDs and hashes
retain lineage after renaming. Snapshots are stored directly, not as recursively
nested ZIPs. A revision is saved on export, **not on every mouse click or edit**.
Its ID hashes the normalized snapshot manifest; the model hash is recorded separately.
Earlier payloads and review records are retained, including rejected attempts.

## Reviewing and comparing

The form shows Accepted, Needs changes and Rejected, a comment and optional
specific comments. Choose **Start review on live network** for the selected saved
version. With **Add finding** enabled, clicking an element or dragging an area
opens the finding form immediately; confirming adds it to the review list.
**Inspect parameters** shows selected element inputs without changing the network;
**All parameters** lists the complete model. Middle-drag pans, wheel scrolls and
Ctrl/Cmd+wheel zooms. The live canvas opens maximized and also supports the standard maximize/restore controls. Saved images remain in the ZIP and are accessible through MCP.
A review with findings keeps its target fixed until those findings are removed;
use another review session for a different version. For findings on an edited
current network, export and reopen its snapshot first. Canceling a finding adds
nothing. At save time, the review shows the accumulated list.
Source image/element identifiers and region coordinates stay in the archive and
are not editable technical fields in the human form. Task/history text uses plain
review records, images use network names and revisions use version numbers. JSON,
checksums and geometry remain available through MCP and the archive. The default target is the
**loaded ZIP revision matching its stored images**. Choose the current-model target
when reviewing the new capture instead. The record distinguishes `revision_id`
(the reviewed evidence) from `result_revision_id` (the newly saved snapshot).

The human form always saves `reviewer_kind=human`; it has no reviewer-type selector. Agents use MCP with `reviewer_kind=agent`; MCP can also record explicitly supplied human feedback as `human`. The exporting contributor is recorded
separately as `submitted_by`. Decisions never automatically promote old manifest
status or mark simulation verification as passed. Acceptance of an older revision
does not approve later edits.

The **Versions and comparison** tab browses saved evidence without replacing the
working canvas. Select two versions and use **Show on canvases** to open two
independently movable read-only canvases, with ordinary panning, zoom and nested
network navigation. The comparison text shows element names and readable marking,
parameter and optional layout changes; full element-ID/XML paths are available
through the MCP comparison tool. Formatting and attribute order
are ignored. Reidentified elements may appear as removal/addition; this is an
observed document diff, not proof of equivalent simulation behavior.

Imports validate file hashes, revision/review chains and safe entry paths before
replacing the document. Limits are 10,000 files, 64 MiB per entry and 256 MiB total
uncompressed data. Overflow fails explicitly without dropping old evidence. Hashes
detect corruption; they are not reviewer signatures.

## Simulation evidence

The live window uses the shared canvas in REVIEW mode: inspect/select, pan/zoom,
nested object navigation and the normal parameter forms with writes disabled.
The shared simulation controls provide play/pause, single event, step back,
stop/reset, speed and nonanimated runs. The ordinary statistics monitor also works.
Each window owns its model, simulation backup and results; runs do not edit the
working document or saved evidence.

Before saving a review, automatic simulation is enabled by default at horizons
100, 1000 and 10000, each on a fresh copy of the exact reviewed PNML. The
**Simulation** tab configures horizons and sampling interval. Disabling it requires
a nonblank reason. A validation/runtime error counts as an attempted run and is
preserved. Failed, interrupted and incomplete runs never certify correctness.

Every new review record includes `simulation`: source PNML hash, skip reason,
`automatic_runs` and runs observed in its live review/comparison windows.
Each run stores start/horizon, status/error, elapsed wall time, initial and final
states/statistics, bounded protocol text, and a trajectory. Its `columns` identify
objects, places/transitions and metrics; each `samples` row is `[time, ...values]`.
Place values are tokens, transition values are active channels. Sampling defaults
to every unit of model time, using the state after events at the same timestamp
and holding it constant between events. No interpolation is invented.

For large datasets, the effective interval increases to cap 20000 regular rows and
1000000 scalar cells; the requested and effective intervals are recorded. The
non-grid final endpoint may add one row. Automatic runs stop at 10000 event steps
or four seconds each; the record states incomplete coverage. Protocol text is
capped at 65536 characters with an explicit truncation flag. Unknown random seeds
remain unknown, so exact replay of stochastic runs is not guaranteed.

The review's simulation results target `revision_id`, which may differ from the
newly saved result version. Earlier records remain unchanged. Root
`verification.json` stays unknown: observed execution is evidence, not an assertion
that the model satisfies its task. Finish/stop an interactive run before saving.

## MCP

Use the `review_petri_model` prompt or read `petri://desktop/training-review` for the
editor adapter. The generic shared `petri-training-review` skill is available when
its shared package update is installed. This feature does not publish that package.

| Tool | Purpose |
|---|---|
| `open_training_bundle` | Validate/open a ZIP; replacing a nonempty document requires `replace_current=true` |
| `inspect_training_bundle` | Read task, views, revision IDs and reviews |
| `training_bundle_image` | Read a current or historical stored PNG as MCP image content |
| `compare_training_revisions` | Compare saved revision IDs; `head` is the loaded snapshot, not unsaved edits |
| `export_training_review` | Capture evidence and append a review to a new ZIP |
| `export_training_data` | Save evidence without adding a review, preserving loaded history/context |
| `get_contributor_profile`, `update_contributor_profile` | Read or explicitly update the local reusable profile |

For review export supply `output_directory`, `reviewer_kind`, `decision` and
`comment`, plus a reviewer name for an agent (human reviews can use the saved
nickname). Findings use `severity`, `message`, optional `element_id` and `view_id`;
linked elements must exist in the reviewed revision and view. Omit `revision_id`
for the loaded revision, use `current` for the new capture, or an existing revision
hash for older evidence. Actual new `conversation` messages append to the transcript.

Ordinary training export requires `title` and `output_directory`; supplied context
fields override imported values, omitted fields retain them. Review status defaults
to `unreviewed`. The recorder observes selected MCP operations since this document
was opened, with a 200-call/1 MiB limit. A snapshot precedes its own export response.
The application cannot recover an external client's private chat or complete session
history; direct GUI editing is not recorded action by action. Missing-data notes
state these boundaries instead of inventing a transcript or successful checks.

## Configurable standalone images

**File > Export image...** offers the active view, complete expanded document,
selected object networks, or selected places/transitions/objects with their internal
arcs. Choose one PNG or a ZIP of images with `images.json` geometry. Configure object
selection, overview inclusion, scale (up to 4), transparent background and maximum
dimension (256-8192 pixels); pixel limits still apply.

MCP `export_network_image` has matching options: `scope` is `active`, `document`,
`objects` or `selection`; `object_indices` selects zero-based objects (omitted: all),
`include_overview` controls the overview for object exports, and `scale`,
`transparent_background`, `max_dimension` configure rendering. `output_path` ends
in `.png` or `.zip`; replacing an existing file requires `overwrite=true`. PNG text
metadata records scope, origin, scale and dimensions. Empty selections fail clearly.

## Preparing LLM training data

The ZIP is source evidence, not a ready-made fine-tuning dataset. Curate sequences
such as task → attempted model/images → review → corrected model. Rejected attempts
can be correction inputs or negative examples, not correct target images. Keep a
bundle's related revisions/views together in one training/evaluation split.

Geometry remains `unverified`. Coordinates and body bounds do not automatically
become accepted detection labels, and text comments do not become bounding boxes.
A separate reviewed annotation/verification conversion is required for supervised
detection. Shared schemas and the review guide document the portable provenance.
