# LSPosed adaptive polish

This pass keeps the LSPosed APK module surfaces readable after the Repository and Modules tabs gained install review, safety guidance, snapshots, and version governance.

## Layout contract

- Phones keep the current single-column flow.
- Medium/expanded layouts use a two-pane structure:
  - left pane: searchable module list
  - right rail: LSPosed guidance, summary metrics, and snapshot controls
- Description text remains visible on cards and stays capped to the phone readability contract.
- Button groups use wrapping rows so Install, Source, Website, Open app, Open LSPosed, and Update do not clip on compact screens.

## Screenshot contract coverage

`LsposedAdaptiveUiContractTest` protects the adaptive breakpoint, side-rail minimum width, description line cap, and visible safety-notice limit. These are source-level contracts that keep screenshot surfaces from drifting into cramped card layouts before full screenshot automation is added.
