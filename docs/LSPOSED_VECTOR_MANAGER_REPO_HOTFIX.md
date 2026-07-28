# LSPosed Vector Manager and Repository Hotfix

This hotfix extends the completed LSPosed provider stack for Vector v2 manager builds.

## Vector manager recognition

The inspected Vector release bundles `manager.apk` with package `org.matrix.vector.manager`. Its launcher activity accepts `android.intent.action.MAIN`, normal launcher/default categories, and the Vector-specific `org.matrix.vector.manager.LAUNCH_MANAGER` category. MMRL now includes that package in manifest package visibility and the LSPosed manager allowlist, then falls back to a category-based launch intent when `getLaunchIntentForPackage` does not expose a normal launcher result.

The provider action bridge remains intact. If the active root provider exposes `action.sh`, MMRL can still run the provider bridge when a manager app launch is unavailable.

## Repository handling

MMRL still treats `https://modules.lsposed.org/modules.json` and `https://modules.lsposed.org/module/<package>.json` as the documented primary LSPosed/Xposed repository API. It now adds `https://backup.modules.lsposed.org/` before the generated `gh-pages` jsDelivr mirror so transient 403 or edge failures have an LSPosed-family fallback before falling to the CDN mirror.

The app-wide GitHub token remains applied only to GitHub hosts and GitHub API requests; repository JSON from `modules.lsposed.org`, `backup.modules.lsposed.org`, and jsDelivr is fetched without leaking the token.
