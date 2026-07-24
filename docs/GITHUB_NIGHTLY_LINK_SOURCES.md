# GitHub nightly.link sources

MMRL supports three GitHub source modes for module repositories:

- **Release** resolves the latest normal GitHub release asset.
- **Nightly** resolves the latest successful GitHub Actions artifact through the GitHub API. This may require a saved GitHub token for artifact downloads.
- **Nightly.link** resolves the same successful Actions artifact, but stores the downloadable artifact URL through `nightly.link` so public repositories can avoid GitHub's login-gated artifact downloads.

For public Actions artifacts, nightly.link provides stable links for the latest successful run using:

```text
https://nightly.link/<owner>/<repo>/workflows/<workflow>/<branch>/<artifact>.zip
```

The resolver builds that URL from the GitHub run workflow path, branch, and artifact name. The artifact archive is normalized before publishing: nested ZIPs are extracted, wrapped module directories are repacked, and archives with `module.prop` at the root are accepted directly.

Installed modules linked to GitHub can edit their source entry from the module overflow menu. Switching between Release, Nightly, and Nightly.link updates the stored source URL and refreshes the repository metadata without changing the installed module files.
