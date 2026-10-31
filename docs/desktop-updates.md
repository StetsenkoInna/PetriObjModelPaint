# Desktop updates

Starting with 2.7.0, the packaged desktop application checks the latest stable GitHub
release once when it opens. There is no periodic check, timer or check on window focus.
Startup remains usable while the check runs; offline and incomplete releases are ignored.
Source and IDE runs do not check for updates.

An available update offers three choices:

- **Install update** downloads and verifies the archive, offers to save the current model,
  then restarts the application to finish installation.
- **Remind me tomorrow** suppresses that build for 24 hours. The next check still happens
  only when the application opens, after the reminder time has passed.
- **Skip this build** suppresses that exact build. A newer version or a rebuilt archive
  under the same tag can still be offered.

Closing the notice or pressing Escape means **Remind me tomorrow**. Preferences can
disable **Check for updates when the application opens**. Nothing is downloaded until
the user clicks Install. Cancelling a download or the save prompt leaves the application
running on the current version. Stop any simulation before restarting.

Installation replaces the current application JAR in its existing folder. It requires a
writable installation folder and the supported platform/CPU architecture. A helper runs
from a separate copy so Windows can release the installed JAR before replacement.
The old JAR is kept as `<application>.jar.previous`; a failed replacement or immediate
startup failure restores it. Saved models, UI settings, MCP configuration and other files
in the installation folder are not replaced. A model saved during update preparation is
reopened after restart even if normal automatic reopening is disabled.

The application validates the archive SHA-256, extracted JAR SHA-256, application entry
point, version and CPU metadata before handing it to the installer. Downloads are restricted
to this repository's HTTPS release URLs. The installer verifies the JAR again before
changing files. This verifies integrity against the GitHub release metadata; it is not an
independent code-signing system.

The release workflow creates `desktop-update.json` only after the Windows, Linux and macOS
archives succeed. It contains each archive's URL, size, SHA-256, JAR SHA-256 and architecture.
The current installed JAR's hash detects replacements under the same release tag.
The checker uses the [GitHub Releases API](https://docs.github.com/en/rest/releases/releases).

Versions through 2.6.0 have no updater. Install 2.7.0 once from the downloaded archive;
subsequent supported releases can be installed from the startup notice.
