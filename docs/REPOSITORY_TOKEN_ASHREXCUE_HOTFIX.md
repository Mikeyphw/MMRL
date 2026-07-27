# Repository token and AshReXcue hotfix

This overlay fixes three user-visible problems after the LSPosed provider refresh bridge:

- LSPosed/Xposed repository refresh now tries the documented `modules.lsposed.org` API first, then falls back to the historical jsDelivr mirror for the generated `gh-pages` repository payload when the primary endpoint returns HTTP 403 or another transient failure.
- GitHub-backed repository, detail, APK, nightly, and artifact requests share the existing encrypted `GitHubTokenStore`, so one token can reduce GitHub 403/rate-limit failures across the app.
- AshReXcue installed-state matching canonicalizes known historical module IDs such as `ashlooper`, `ashrexcue`, and `ashrexcuebootloopprotector`, so the app recognizes the bundled/rebranded module as installed even if the repository ID differs from the live root module folder.

The app-wide token setting lives in Settings > Other. Tokens are stored through Android Keystore via `GitHubTokenStore`; they are not serialized into the UserPreferences protobuf.
