# UI topology and density seal

This pass tightens the post-LSPosed UI surface without adding new feature surface.

## Contracts

- Repository root/GitHub lists do not re-apply toolbar top padding after the tab row.
- Installed root module cards keep WebUI and Action as visible actions only; they are not duplicated as status chips.
- Module actions wrap with `FlowRow` instead of hidden horizontal scrolling.
- The device summary card stays metric-focused and no longer carries snapshot buttons.
- Shared status pills expose state semantics without pretending to be images.
- GitHub source dialog labels are resource-backed.
- LSPosed installed phone content prioritizes installed modules before guidance and snapshot controls.
