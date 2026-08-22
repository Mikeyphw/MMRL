# Repository token hotfix

This hotfix keeps repository fallback and GitHub authentication behavior deterministic.

- LSPosed/Xposed repository refresh tries the documented `modules.lsposed.org` API first, then the backup endpoint and generated jsDelivr mirror when the primary endpoint returns HTTP 403 or another transient failure.
- GitHub-backed repository, detail, APK, nightly, and artifact requests share the existing encrypted `GitHubTokenStore`, so one token can reduce GitHub 403/rate-limit failures across the app.

The app-wide token setting lives in Settings > Other. Tokens are stored through Android Keystore via `GitHubTokenStore`; they are not serialized into the UserPreferences protobuf.
