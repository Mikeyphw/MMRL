# Local replacements for private MMRL packages

This tree no longer depends on the inaccessible GitHub Packages artifacts:

- `dev.mmrlx:terminal:1.0.152`
- `dev.mmrlx:webui.core:1.0.152`

They are replaced by `:terminal-compat` and `:webui-core-compat` while preserving the package names used by the app.

## Terminal behavior

The terminal module executes real root commands through `su -c`, streams stdout and stderr concurrently into an observable Compose terminal buffer, propagates non-zero exit status through Kotlin `Result`, supports cancellation cleanup, strips terminal control sequences, and caps retained output.

The MMRL action/install flows are non-interactive, so this replacement intentionally does not emulate a full interactive PTY.

## WebUI behavior

The WebUI module provides the state/configuration API used by `ViewDescriptionScreen`, a real Android `WebView`, reflective path-handler registration, internal URL interception, generated inset CSS, lifecycle cleanup, and the existing asset/readme routing.

No GitHub package credentials are required after this change.
