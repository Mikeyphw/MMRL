# AshReXcue Phase G: Module Intelligence Integration

Phase G carries AshReXcue recovery evidence into MMRL's normal module browsing and update workflows. The Recovery Center remains the authority for recovery actions, while installed-module and module-detail surfaces now explain why a module deserves attention before the user changes it.

## Per-module intelligence

A pure intelligence engine derives one record for every module in the typed AshReXcue snapshot. It reuses Phase E culprit evidence whenever a ranked candidate is available and supplies conservative fallback evidence for modules outside the guidance top ten.

Each intelligence record includes:

- the declared module ID and actual folder;
- trust and quarantine state;
- changed-since-stable state;
- a 0–100 evidence score;
- low, elevated, high, or critical risk band;
- an explainable summary;
- a recommended next action;
- the strongest supporting factors; and
- live, cached, and read-only provenance.

Protected modules retain policy context and are never presented with destructive recommendations.

## Installed modules

The installed-module list now exposes:

- risk-aware AshReXcue badges;
- a **Needs review** filter for quarantined, changed, suspect, or high-risk modules;
- a **Changed** filter for modules whose fingerprint differs from the last stable snapshot; and
- normal-state filtering that excludes modules with unresolved recovery evidence.

Module identity is correlated through both the declared ID and actual folder name.

## Module details and update safety

Installed repository modules receive an AshReXcue intelligence card containing:

- current risk band and evidence score;
- trust, quarantine, changed, and cached-state indicators;
- the top recovery evidence factors;
- the recommended recovery action;
- a direct route to Recovery Center; and
- a guarded shortcut to mark a high-risk module suspect.

The card also evaluates an available update against both installed recovery evidence and the update's declared sensitive surface:

- boot-time scripts;
- native or Zygisk code;
- SELinux policy changes;
- system property changes; and
- bundled APK payloads.

This assessment is advisory. Phase G does not silently block installs or mutate module state.

## Warning cleanup

The installed-module cover loader now opens root-backed files with `SuFileInputStream` instead of the deprecated `SuFile.newInputStream()` API.

## Compatibility and storage

- No AshReXcue module update is required.
- Snapshot schema remains version 1.
- No Room entity, DAO, schema, or migration changes are required.
- No Gradle dependency or native build changes are required.
