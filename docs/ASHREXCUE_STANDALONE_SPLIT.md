# AshReXcue standalone split

## Overlay 01: application boundary

AshReXcue is being separated from MMRL into its own Android application.

This first overlay deliberately does **not** move the existing `com.dergoogler.mmrl.ash`
implementation. The current implementation is bidirectionally coupled:

- MMRL screens, view models, startup logic, Tasker integration, and unified module models
  import AshReXcue implementation/models directly.
- AshReXcue currently imports MMRL-owned UI components, installer/staging helpers,
  operation history infrastructure, app notifications/activities, preferences, and resources.

Moving the package before breaking those dependencies would either make the new app depend on
`:app` or force a broad rewrite in one overlay. Both defeat the purpose of the split.

Overlay 01 therefore establishes these invariants:

1. `:ashrexcue` is an independent Android application module.
2. It has its own application ID: `com.mikeyphw.ashrexcue`.
3. It has no dependency on MMRL's `:app` module.
4. A verification task rejects imports of MMRL app-owned implementation packages from the
   standalone app.
5. Existing MMRL/AshReXcue runtime behavior is unchanged in this overlay.

## Migration direction after Overlay 01

- Extract pure Ash protocol/domain models that both apps need into a lower-level contract module.
- Move Ash root transport, snapshot/cache, module lifecycle, and bundled module ownership into
  `:ashrexcue`, replacing MMRL app-owned dependencies with Ash-owned implementations.
- Convert MMRL's direct Ash imports to a narrow cross-app integration protocol (explicit intents
  and/or a typed Binder surface; no generic shell execution).
- Move Ash automation/Tasker ownership and boot receiver behavior to the standalone app.
- Remove the embedded Recovery Center implementation and bundled Ash module packaging from MMRL
  only after the standalone app reaches feature parity.
