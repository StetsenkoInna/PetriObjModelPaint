# Swing editor adapter

The available desktop tools are `inspect_ui`, `screenshot` and `ui_action`.
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

Run and save through the existing application actions. Check the visible error and
simulation output, and report the checks actually performed. Shared knowledge does
not grant an unavailable pattern builder, validator, export tool or chart tool.
