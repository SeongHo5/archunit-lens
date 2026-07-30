---
name: prepare-release
description: Prepare an ArchUnit Lens release pull request without tagging or publishing. Use for version bumps, changelog or README synchronization, release/VERSION branches, or release PR creation.
---

# Prepare Release

Prepare a release PR only. Do not create tags or publish the plugin from this skill. For the complete release flow after a PR is merged, use `$release-plugin VERSION`.

## Workflow

1. Read repo rules before editing:
   - `.codex/rules/project-structure.md`
   - `.codex/rules/build-and-verification.md`
   - `.codex/rules/git-and-review-scope.md`
   - `.codex/rules/commit-messages.md` when committing
2. Confirm scope with `git status --short --branch`.
   - Stop before editing if unrelated work is present and cannot be isolated.
3. Choose the version:
   - Use the user-provided version exactly.
   - Otherwise choose patch for bugfix/regression releases.
   - Choose minor for user-visible features or supported-rule expansion.
   - Choose major only for explicit breaking changes or support-policy resets.
4. Create `release/VERSION` from current `main`.
5. Update the release-prep files:
   - `gradle.properties`: set `version=<version>`.
   - `CHANGELOG.md`: add a dated, user-facing entry.
   - `README.md` and `README.ko.md`: mirror the visible plugin version and support-scope heading.
   - `docs/rule-support-matrix.md`: update only when supported rule behavior changed.
6. Verify before PR:
   - `git diff --check`
   - `./gradlew ktlintCheck --no-daemon`
   - `./gradlew test --no-daemon`
   - `./gradlew check --no-daemon`
   - `./gradlew buildPlugin --no-daemon`
   - `./gradlew verifyPlugin --no-daemon`
7. Commit only the release-prep slice:

```text
build(plugin): prepare VERSION release

Tested: <commands run>
Not-tested: <only if a required check was skipped>
```

8. Push the branch and open the PR with `gh`:
   - Read `.github/pull_request_template.md`.
   - Use `gh pr create --base main --head release/VERSION --title "Prepare VERSION release" --body-file path/to/pr-body.md`.
   - Add `--draft` only when required verification is skipped or failing.

## Release Notes Rules

- Keep changelog entries user-facing because Marketplace release notes can use them.
- Exclude dependency, CI, or workflow maintenance unless it changes user-visible distribution behavior.
- Keep unsupported/custom/helper ArchUnit rules described as metadata-only unless implementation and tests prove live-warning support.
- Match existing tag convention when referencing release tags: bare semver such as `0.1.1`, not `v0.1.1`.
