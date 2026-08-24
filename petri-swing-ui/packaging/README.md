# PetriObjModelPaint - Desktop Editor

Petri net graphical editor and simulator (POMP).

To run: double-click the launcher for your OS

- Windows -> `petri-swing-ui.bat`
- Linux -> `petri-swing-ui.sh`
- macOS -> `petri-swing-ui.command`

Requires Java 23 or newer. The launcher checks for it automatically and points
you to <https://www.oracle.com/java/technologies/downloads/> if it's missing.

macOS may refuse to open `petri-swing-ui.command` ("cannot verify this app").
The launcher isn't signed or notarized yet. Options:

- System Settings > Privacy & Security > "Open Anyway", then open the file again.
- `xattr -dr com.apple.quarantine path/to/unzipped-folder`
- Run it from Terminal: `chmod +x petri-swing-ui.command && ./petri-swing-ui.command`

Full documentation: <https://github.com/StetsenkoInna/PetriObjModelPaint>

License: see `LICENSE` in this folder.
