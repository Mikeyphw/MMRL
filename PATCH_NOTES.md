# MMRL local compatibility patch

## Applied changes

- Replaced `dev.mmrlx:terminal:1.0.152` with local `:terminal-compat`.
- Replaced `dev.mmrlx:webui.core:1.0.152` with local `:webui-core-compat`.
- Removed the private `MMRLApp/X` GitHub Packages repository and credential requirement.
- Preserved the `dev.mmrlx.*` package/API names used by the current app source.
- Added real root command execution, concurrent output draining, terminal output state, Compose rendering, cancellation cleanup, path-handler routing, WebView rendering, inset CSS, and lifecycle cleanup.

## Build

```bash
build-apk "$HOME/Code/MMRL"
```

The old `gpr.user` and `gpr.key` entries are no longer required for this project.

## Validation performed

- Checked all current `dev.mmrlx.*` imports against the local API surface.
- Checked for missing private dependency aliases and repository references.
- Parsed `gradle/libs.versions.toml` and all new XML manifests.
- Compiled all new Kotlin source files against API-compatible Android, Compose, and coroutine stubs to catch syntax, overload, constructor, and named-argument errors.

A full Android build was not possible in the packaging environment because Gradle dependencies and the Android SDK were unavailable offline. Run the normal build command above in the configured Android build environment.
