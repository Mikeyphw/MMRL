# LSPosed manager seal

MMRL opens LSPosed/Vector through a sealed order:

1. Prefer a normal installed manager app launch intent.
2. Fall back to the active root provider action bridge when Vector/LSPosed exposes `action.sh`.
3. Surface a bundled `manager.apk` as detected-but-not-directly-openable, instead of pretending it is installed.
4. Keep unavailable providers explicit and non-actionable.

The provider status card now reports the exact manager bridge state, active/staged provider state, and scope DB readability together. This preserves the APK-module boundary while making Vector's hidden-manager/action bridge path visible and reviewable.
