# UI/UX Phase 2 — recovery workflow redesign

Phase 2 turns Recovery Center from a collection of implementation categories into a task-oriented workflow.

## Task destinations

Recovery Center now persists one of six user goals:

- **Check status** — current incident, boot-failure progress, quarantine attention, protection state, and recent sessions.
- **Restore modules** — guarded quarantine restoration with direct conservative, balanced, rapid, and exact one-module plan previews.
- **Find culprit** — ranked evidence, recommendations, classification actions, and guidance feedback.
- **Review trial** — complete or roll back the active restoration trial and review prior trial outcomes.
- **History** — recovery sessions plus the complete MMRL Activity audit trail.
- **Advanced** — compatibility, release readiness, state repair, diagnostic export, settings, and technical history.

Diagnostics are deliberately absent from the normal recovery path and remain available under **Advanced**.

## Guarded quarantine actions

Quarantine no longer redirects to another section. Every preset and per-module restore action opens the guarded plan preview in place. The preview remains useful when a module is stale, protected, missing, or otherwise unsafe because its blockers are shown before execution.

High-risk plans still require their exact confirmation phrase. Plans remain bound to the live recovery revision and are revalidated by AshReXcue before module state changes.

## Independent operation state

The former global `loading` switch is replaced by keyed `AshOperation` entries. Refresh, module preparation, plan execution, trial completion, rollback, trust changes, feedback, settings, state repair, and diagnostic export now expose independent progress.

A running export no longer makes a trial button look busy, and a refresh no longer disables unrelated actions. Duplicate execution of the same operation key remains blocked.

## Dialog and status behavior

- Recovery confirmations and previews use bounded scrollable content.
- Dialogs apply IME-aware padding, including high-risk typed confirmation.
- Noninteractive status labels use `StatusPill` rather than empty-click `AssistChip` controls.
- Module intelligence is compact by default and expands only when evidence or update-safety details are requested.

## Tasker automation isolation

Phase 2 keeps Recovery UI operation state isolated from Tasker and AshReXcue automation request serialization. The overlay does not change Tasker authorization classes or test dependencies, so the existing Ash token-binding round-trip contract remains the source of truth.

## Host unit-test serialization fix

The second validation pass exposed a pre-existing host-unit-test mismatch in `TaskerRootRequest`: its Ash automation token and idempotency-key round trip used Android's `org.json` implementation directly. Local JVM unit tests run against the mockable Android jar, where those JSON methods throw before the token-binding assertion can verify the fields.

This overlay keeps the Tasker/Ash authorization contract intact, but moves `TaskerRootRequest` persistence to the existing Kotlin serialization JSON runtime. Android-facing `JSONObject` input remains accepted for compatibility, while the stored representation and unit-test round trip no longer depend on mocked Android JSON methods.
