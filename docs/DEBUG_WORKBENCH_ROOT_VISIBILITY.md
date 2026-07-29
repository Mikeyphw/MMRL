# Debug Workbench root visibility hardening

Phase 8 exists because support bundles can show a false-looking failure such as:

- `org.matrix.vector.manager` not visible even when a Vector manager APK is installed;
- no provider module found even when `zygisk_vector` or LSPosed is active;
- AshReXcue not detected even when the module works;
- `modules.lsposed.org/modules.json` returning HTTP 403 while the backup endpoint succeeds.

The goal is to make the failing layer visible without adding unsafe controls.

## Root module evidence

Provider and AshReXcue probes inspect both module roots:

- `/data/adb/modules`
- `/data/adb/modules_update`

Each root reports:

- path;
- existence;
- directory status;
- read visibility;
- child count;
- bounded children preview;
- listing error, if any.

Folder names under `/data/adb/modules` are preserved because they are required to diagnose module identity, staged-update, and alias bugs. The preview is bounded so a huge module tree does not flood support bundles.

## Root-aware reads

Phase 8 uses MMRL's existing root-aware file abstraction, `SuFile`, for root module scans and `module.prop` reads. This lets the debug workbench distinguish these cases:

- root directory does not exist;
- root directory exists but cannot be listed;
- root directory lists children but `module.prop` is unreadable;
- `module.prop` is readable but id/name aliases do not match;
- a known provider module exists but expected files are incomplete.

The same root-aware read path is used for runtime provider detection and AshReXcue location so the diagnostic path and runtime path do not drift.

## Vector manager handling

Vector manager visibility is explicit:

- manager package: `org.matrix.vector.manager`
- daemon package: `org.matrix.vector.daemon`
- package-specific launch action/category: `org.matrix.vector.manager.LAUNCH_MANAGER`
- compatibility launch action: `org.lsposed.manager.LAUNCH_MANAGER`

The workbench also reports a visible manager-like package scan for package names containing `lsposed`, `libxposed`, `vector`, or `matrix`. If `org.matrix.vector.manager` is missing but a nearby package appears, the support bundle can reveal the real package name.

## Xposed repository fallback

Runtime LSPosed/Xposed main-index loading uses this order:

1. `https://modules.lsposed.org/modules.json`
2. `https://backup.modules.lsposed.org/modules.json`

The generated jsDelivr `modules@gh-pages/modules.json` main-index path remains a last-resort fallback for compatibility with the sealed repository contracts, but `backup.modules.lsposed.org` is preferred immediately after a primary 403. Support diagnostics should make it obvious when the generated mirror returns 404 so the user does not mistake that last-resort failure for the primary outage.

## Safety contract

Phase 8 is read-only except for normal network repository reads. It must not:

- write or delete module folders;
- edit LSPosed scope databases;
- mutate provider modules;
- save, delete, or print GitHub tokens;
- expose Authorization headers or cookies;
- run arbitrary shell input;
- expand support bundles with unbounded root command output.

## Phase 8 follow-up: null scope and Vector bridge reporting

The LSPosed repository index may expose `scope: null` on individual module records.
MMRL treats null and missing repository scope fields as an empty scope list so the
entire LSPosed module list does not fail with a Moshi non-null decoding error.
This does not grant or edit LSPosed runtime scopes; it only prevents repository
metadata from blocking locally detected Xposed APK modules.

Vector can also expose its manager through the provider `action.sh` bridge instead
of an Android package that PackageManager can see as `org.matrix.vector.manager`.
When the installed manager package is invisible but the active provider action
bridge is present, Debug Workbench now reports that bridge as the available path
rather than only failing the installed-manager package probe.
