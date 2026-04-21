# Release Process

This repository now supports two flows:

- normal backup + code push
- backup + code push + GitHub release + APK archive

## Unified Push Workflow

If you want the full flow that you described for future "push GitHub" requests, use:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\push-github.ps1 -Message "Your commit message"
```

What it does in order:

- creates and pushes an annotated backup tag for the current `HEAD`
- stages and commits all current code changes
- rebases onto the latest `origin/main` when needed, then pushes `main`
- runs the release workflow
- increments app version in `gradle.properties`
- runs `testDebugUnitTest` unless `-SkipTests` is used
- builds `assembleRelease`
- uploads the APK to the GitHub release
- copies the same APK into `apk-archive-repo/releases`
- updates `apk-archive-repo/README.md`
- commits and pushes the APK archive repository

Optional flags:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\push-github.ps1 -Message "Your commit message" -Version 1.12
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\push-github.ps1 -Message "Your commit message" -SkipTests
```

## Backup Workflow

If you want every normal code submission to keep the previous version on GitHub, use the backup script from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\backup-and-push.ps1 -Message "Your commit message"
```

What it does:

- Creates an annotated backup tag for the current `HEAD`
- Pushes that backup tag to GitHub first
- Stages all local changes
- Commits the new change
- Pushes `main`

Backup tags are named like:

- `backup-20260418-231500-81fb666`

That means each time you use this script, the old version is preserved on GitHub before the new commit moves `main`.

## Rules

- Version tags use `v1.0`, `v1.1`, `v1.2` in order.
- The app version is stored in `gradle.properties`:
  - `APP_VERSION_NAME`
  - `APP_VERSION_CODE`
- Every GitHub release keeps exactly one APK asset:
  - `Timetable-vX.Y.apk`
- GitHub releases currently use the Android `debug` keystore so the APK stays installable and the signing stays consistent.

## Command

Run the release script from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\publish-release.ps1
```

Default behavior:

- Reads the current version from `gradle.properties`
- Increments the minor version automatically
  - `1.1` -> `1.2`
- Increments `APP_VERSION_CODE`
- Runs `testDebugUnitTest`
- Runs `assembleRelease`
- Copies the final APK to `app/build/release-assets/Timetable-vX.Y.apk`
- Commits the version bump
- Pushes `main`
- Creates and pushes the Git tag
- Creates or updates the GitHub release
- Uploads the single APK asset

## Optional flags

Publish a specific version:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\publish-release.ps1 -Version 1.2
```

Skip unit tests:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\publish-release.ps1 -SkipTests
```

## Notes

- The script requires a clean git working tree.
- The script reads the GitHub token from the configured git credential helper.
- If a dedicated release keystore is added later, only the signing section in `app/build.gradle.kts` and the release note text need to change.
