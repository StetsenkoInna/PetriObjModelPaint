# Swing editor adapter

Prefer direct model tools: `get_model`, `apply_model`, `edit_model`, `validate_model`,
`simulate_model`, `open_model` and `save_model`. These operations need no UI inspection.
Read `get_model` first and use its revision in changes. `apply_model` accepts a complete
flat document with name, places, transitions and arcs. IDs are stable XML NCNames;
arcs address nodes by ID. Coordinates are node centers. `edit_model` accepts atomic
put_place, put_transition, put_arc and remove operations; puts partially update existing
elements. A failed batch leaves the canvas unchanged. Existing documents require
replace_current=true for apply/open; stale revisions are rejected. Changes support Undo.

Structured edits are for flat concrete nets. Preserve objects, links and symbolic
parameters by reading include_pnml=true and using `open_model` with revised PNML.
`validate_model` checks engine compatibility, not task correctness or schema conformance.
`simulate_model` returns observed evidence from a fresh copy and leaves the canvas
markings unchanged. It has bounded execution and reports failed or incomplete runs.
`save_model` writes PNML to an absolute path; overwrite=true is required for existing files.
Delay and horizon values use the same model clock. Uniform deviation is the half-range;
normal deviation is the standard deviation. A read arc tests availability, not absence.

UI tools remain available: `inspect_ui`, `screenshot`, `ui_action`,
`export_training_data` and `export_network_image`. The following applies to UI actions.
The shared modeling guides describe concepts, not additional tool names.

Inspect the current document and windows before choosing an action. Use the component
IDs returned by the latest inspection. Read menus and tooltips to find the editor's
existing creation, property, simulation, undo and save actions. Use a window screenshot
to understand canvas geometry. Mouse coordinates are relative to the chosen component.

Use the editor's controls and property dialogs to apply the design. The time-unit
chips only label the model clock; changing them does not convert existing delays or
the horizon. Choose one unit and convert all relevant numeric parameters consistently
when needed. This desktop API has no `time_unit` argument or automatic pattern
auto-connection. Inspect and verify the arcs actually
created on the canvas. Petri-object composition uses the editor's own object controls.

Actions are queued on the Swing event dispatch thread. Inspect again to observe
completion, errors and modal dialogs before the next action. Handle the innermost
dialog first. A native file dialog accepts a path through the `file` action. An
existing-file save still asks for confirmation. Do not assume a queued action succeeded.

When using UI actions, run and save through the existing application actions. Check the visible error and
simulation output, and report the checks actually performed. Shared knowledge does
not grant an unavailable pattern builder, validator, export tool or chart tool.

When requested, use `export_network_image` for a standalone image of the active
canvas or `export_training_data` for the current PNML and supplied session context.
Supply only actual conversation messages. A bundle is unreviewed by default;
export success is not a passed model check. Geometry is unverified rather than
reviewed detector labeling. Follow `petri://skills/petri-training-export` for the
shared archive contract.
