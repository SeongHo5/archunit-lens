---
name: release-plugin
description: Cut and publish an ArchUnit Lens version end-to-end. Use when the user asks to release, tag, publish, or ship a specific plugin version to JetBrains Marketplace.
---

# Release Plugin

Run the complete release flow: prepare a release PR, merge it, tag the merged commit, and verify Marketplace publishing. The version supplied by the user is authoritative.

## Guardrails

- Use a clean worktree based on current `main`. Keep unrelated changes out of the release slice.
- Use bare semver tags such as `0.1.4`; do not use `v0.1.4`.
- Never tag a release branch or an unmerged commit. Tag only the merged release-prep commit on `main`.
- Never move, delete, or reuse an existing remote tag. Marketplace versions are immutable; recover with a new patch version.
- Stop before tagging if any required verification or PR check fails. Do not bypass branch protection or required review.

## Phase 1: Prepare and merge the release PR

1. Read `.codex/rules/project-structure.md`, `.codex/rules/build-and-verification.md`, `.codex/rules/git-and-review-scope.md`, and `.codex/rules/commit-messages.md`.
2. Inspect `git status --short --branch`, `git fetch origin --tags`, and the current tags. Isolate unrelated work before continuing.
3. Classify the version only when the caller did not provide one: patch for bug fixes, minor for user-visible features or supported-rule expansion, and major for explicit breaking changes or support-policy resets.
4. Create `release/VERSION` from current `main`.
5. Update only the release files:
   - Set `version=VERSION` in `gradle.properties`.
   - Add dated, user-facing notes to `CHANGELOG.md`.
   - Update the visible version and support-scope headings in `README.md` and `README.ko.md`.
   - Update `docs/rule-support-matrix.md` only when supported rule behavior changed.
6. Run this release gate with JDK 21:

```bash
git diff --check
./gradlew ktlintCheck --no-daemon
./gradlew test --no-daemon
./gradlew check --no-daemon
./gradlew buildPlugin --no-daemon
./gradlew verifyPlugin --no-daemon
```

7. Commit only that slice as `build(plugin): prepare VERSION release`, push the branch, and create a non-draft PR to `main`. Include the exact verification commands and whether rule support changed.
8. Wait for required PR checks and merge only after they pass. If the repository requires review or blocks the merge, report that exact blocker.

## Release-notes template

Use this normal-release shape, based on the 0.1.3 entry's concise user-facing style:

```markdown
## [VERSION] - YYYY-MM-DD

Patch release focused on USER-VISIBLE OUTCOME.

### Fixed

- USER-VISIBLE CHANGE.
```

For an immutable Marketplace republish, use this variant instead:

```markdown
## [VERSION] - YYYY-MM-DD

Patch release that republishes the PREVIOUS_VERSION plugin updates under a new immutable Marketplace version.

### Fixed

- Kept the published plugin behavior aligned with PREVIOUS_VERSION after the release pipeline retry required a new Marketplace version.
```

Exclude dependency, CI, and workflow maintenance unless it changes user-visible distribution behavior. Keep unsupported/custom/helper rules described as metadata-only unless implementation and tests prove live-warning support.

## Phase 2: Tag the merged release

1. Refresh `main` and identify its merged release-prep commit.
2. Confirm `origin/main` contains `version=VERSION`, the matching changelog heading, and matching README versions.
3. Confirm `git ls-remote --tags origin VERSION` returns no existing tag.
4. Create and push the bare tag on that exact `origin/main` commit:

```bash
git tag VERSION origin/main
git push origin VERSION
```

5. Re-read the tagged `gradle.properties` and confirm its version equals the tag before treating the release as published.

## Phase 3: Verify publication

1. Watch the tag-triggered `Publish Plugin` workflow to completion.
2. Confirm its `check`, `buildPlugin`, `signPlugin`, `verifyPlugin`, and `publishPlugin` steps passed.
3. Confirm the matching tag-triggered plugin-verification workflow completed and its artifacts are available.
4. Confirm the Marketplace shows `VERSION` when the listing is accessible.
5. Report the PR URL, merge commit, tag, workflow URLs, Marketplace result, and any validation gap.

If publishing fails after the tag exists, do not retag. Capture the failed workflow evidence, fix the cause on `main`, and start a new patch release.
