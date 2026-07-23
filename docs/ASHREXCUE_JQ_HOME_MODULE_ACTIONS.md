# AshReXcue jq, Home access, and module quick actions

This hotfix keeps the post-Activity-navigation shell honest and makes the
installed modules list more direct.

## Home access

Root workflow top bars use a visible text `Home` button instead of a glyph-only
action. Nested/detail destinations should continue to use their normal Back
behavior.

## AshReXcue bundled jq

AshReXcue 11.6.1 bumps the bundled module version so devices with a stale
installation are offered an update/reinstall path. The control script still
repairs executable permissions when `jq/jq` exists, and it can borrow the staged
update copy during read-only diagnostics after a module update is staged but
before reboot.

If `/data/adb/modules/AshLooper/jq/jq` is missing entirely on the active module,
users should reinstall or update the bundled AshReXcue module from MMRL. The
newer error copy says that directly instead of only reporting a missing jq
runtime.

## Installed module actions

Installed module cards always show direct WebUI and Action buttons whenever the
module advertises those capabilities. The overflow menu keeps the same actions
for users who expect them there, but the primary module flow no longer hides
available module entry points.
