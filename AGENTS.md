# Codex Entry Point

This file is the compact Codex/root adapter for this repository. Keep detailed local rules in `.codex/rules/`, not here.

## Read Order

1. Read this file for repository-level instructions.
2. Before repository changes, read the applicable files in `.codex/rules/`.
3. Use the Local Rule Gate to choose rule files.

## Local Rule Gate

### MUST

- Treat applicable `.codex/rules/*` files as a pre-edit gate.
- Re-run the local rule gate after compaction, handoff, session restart, or continuing work from another agent's summary.
- Verify rule conformance explicitly before a final report or commit.
- Compare `git status`, staged files, and commit contents against the requested scope before reporting or committing.

### MUST NOT

- Do not treat autonomy, workflow, summary, or orchestration instructions as a replacement for `.codex/rules/*`.
- Do not assume a previous agent or earlier session already read the applicable local rules.
- Do not include unrelated working-tree changes in a report or commit because they were present during the task.
- Do not duplicate full local rule bodies into `AGENTS.md`; route to `.codex/rules/*`.

### CONDITIONAL

IF changing repository files:

- Read `.codex/rules/project-structure.md`.
- Read `.codex/rules/build-and-verification.md`.

IF editing Kotlin production code, plugin resources, inspections, quick fixes, or rule models:

- Read `.codex/rules/kotlin-style.md`.
- Read `.codex/rules/intellij-plugin-contract.md`.
- Read `.codex/rules/archunit-support-scope.md` when touching ArchUnit rule parsing or support behavior.

IF adding a feature, refactoring plugin runtime behavior, changing inspections, quick fixes, tool windows, settings, caches, parser support, or touching `plugin.xml`:

- Run the `intellij-platform-auditor` agent before final report.
- Treat a `BLOCK` verdict as a failed gate.

IF changing only docs, rules, comments, or non-runtime metadata:

- Skip `intellij-platform-auditor` unless IntelliJ runtime behavior changes.

IF editing tests or fixtures:

- Read `.codex/rules/testing.md`.

IF editing repository rules, agent instructions, or rule-governed docs:

- Read `.codex/rules/rule-authoring.md`.

IF preparing a final report, commit, or pull request:

- Read `.codex/rules/git-and-review-scope.md`.
