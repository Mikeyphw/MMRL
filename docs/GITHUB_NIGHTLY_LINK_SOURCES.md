# GitHub nightly sources

MMRL supports two GitHub source modes for module repositories:

- **Release** resolves the latest normal GitHub release asset.
- **Nightly** resolves the latest successful GitHub Actions artifact through the GitHub API. This may require a saved GitHub token with Actions read access.

Nightly sources store the GitHub repository URL with `?mmrlSource=nightly`. During refresh, MMRL reads successful Actions runs, selects non-expired artifacts, applies the optional artifact-name regex, and stores the GitHub artifact API ZIP URL.

The artifact archive is normalized before publishing: nested ZIPs are extracted, wrapped module directories are repacked, and archives with `module.prop` at the root are accepted directly.

Installed modules linked to GitHub can edit their source entry from the module overflow menu. Switching between Release and Nightly updates the stored source URL and refreshes the repository metadata without changing installed module files.
