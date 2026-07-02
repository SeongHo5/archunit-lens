# Commit Messages

## MUST

- Use Conventional Commit subjects:

```text
<type>(<scope>): <imperative summary>
```

- Omit `(scope)` only when no narrow repo area applies.
- Keep the subject short, outcome-focused, and written in the imperative mood.
- Use these default types:
  - `feat` for user-visible features or supported-rule expansion.
  - `fix` for bug fixes.
  - `docs` for documentation-only changes.
  - `test` for test-only changes.
  - `refactor` for behavior-preserving code structure changes.
  - `perf` for performance improvements.
  - `chore` for maintenance/configuration changes.
  - `ci` for GitHub Actions or CI workflow changes.
  - `build` for Gradle, packaging, or plugin distribution changes.
- Use practical repo scopes such as `tool-window`, `inspection`, `rules`, `parser`, `quickfix`, `settings`, `readme`, `dependabot`, `plugin`, or `ci`.
- Keep the Lore trailers after the subject/body when they add decision context:

```text
Constraint: <external constraint that shaped the decision>
Rejected: <alternative considered> | <reason for rejection>
Confidence: <low|medium|high>
Scope-risk: <narrow|moderate|broad>
Directive: <forward-looking warning for future modifiers>
Tested: <what was verified>
Not-tested: <known gaps in verification>
```

## EXAMPLES

```text
fix(tool-window): move rule overview refresh off EDT
chore(dependabot): label dependency update PRs
docs(readme): clarify supported ArchUnit subset
test(parser): cover forbidden annotation rules
ci(plugin): verify plugin on release tags
```

## MUST NOT

- Do not use vague subjects such as `update`, `fix stuff`, or `changes`.
- Do not include unrelated working-tree changes in the commit.
- Do not use `!` for breaking changes unless the plugin behavior or public contract actually breaks compatibility.
