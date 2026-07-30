# GitHub module sources Phase 9 + 9B

This phase makes GitHub module sources content-shape aware and editable.

## Artifact content shapes

The resolver no longer assumes every GitHub Actions artifact contains a nested module ZIP. It now accepts these installable shapes:

- direct module ZIP, with `module.prop` at the archive root
- nested module ZIP inside the GitHub artifact archive
- extracted module layout at artifact root, such as Vector's `module.prop`, `manager.apk`, and `zygisk/` entries
- single-folder module layout, such as `Vector-Release/module.prop`, which is repacked before install

Invalid artifacts are rejected with evidence that includes the selected strategy, entry count, nested ZIP count, module.prop locations, module root, and reason.

## Vector and ReZygisk behavior

- `PerformanC/ReZygisk` can still install direct ZIP-shaped artifacts.
- `JingMatrix/Vector` can install GitHub Actions artifacts that contain extracted module files instead of a nested ZIP.
- Mapping, symbol, source, and debug artifacts can be rejected by saved source rules.

## Editable saved source rules

Saved GitHub module sources can be edited inside the app through the module source dialog. The source URL stores rules as query parameters so no database migration is required.

Supported rules:

- source mode: release or nightly
- include pre-releases for release mode
- legacy generic file regex
- asset regex for release assets
- artifact regex for Actions artifacts
- reject regex
- preferred variant regex
- branch regex
- workflow regex
- artifact strategy: auto, direct ZIP, nested ZIP, extracted module layout, or single-folder module layout

Rules are validated before saving. Regex values are length-limited and are not mixed with the app-wide GitHub token. Tokens remain in `GitHubTokenStore` and are not exported through saved source URLs.

## Shared download policy

The repository resolver and final download service now share `GitHubArtifactArchivePolicy.materializeModuleZip`, so preview/update resolution and actual publishing use the same artifact-shape rules.
