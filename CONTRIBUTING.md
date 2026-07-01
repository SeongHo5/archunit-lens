# Contributing to ArchUnit Lens

Thanks for helping improve ArchUnit Lens. The plugin is intentionally conservative: ArchUnit tests remain the source of truth, and the IDE inspection is early feedback for statically provable Java DSL subsets only.

## Development environment

- Use JDK 17 for local IntelliJ Platform test runs unless a task explicitly targets the CI matrix.
- CI uses JDK 21 to match the repository workflow baseline.
- Use the Gradle wrapper from the repository root.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat test
.\gradlew.bat ktlintCheck
.\gradlew.bat check
.\gradlew.bat buildPlugin
.\gradlew.bat verifyPlugin
```

On Unix-like shells, use the same task names with `./gradlew`.

## Rule-support policy

New live-warning support is accepted only when all of the following are true:

1. The rule can be interpreted from static Java PSI evidence.
2. No ArchUnit rule, project helper method, lambda, `DescribedPredicate`, or `ArchCondition` implementation needs to be executed.
3. Resolve failures or ambiguous chains produce no live warning.
4. Parser/discovery tests and inspection fixtures prove positive, negative, and no-warning behavior.
5. `docs/rule-support-matrix.md`, README files, inspection descriptions, and release notes stay aligned.

Unsupported or uncertain chains should be retained as metadata for the Rule Overview when possible, but they must not emit editor warnings.

## Test and fixture conventions

- Production Kotlin code lives under `src/main/kotlin/io/github/archunitlens`.
- Tests mirror that package structure under `src/test/kotlin` and use JUnit 4 plus IntelliJ Platform fixtures.
- Java fixture inputs belong in `src/test/testData`; move high-value inline snippets there when changing a scenario.
- Name tests after the rule or quick fix behavior they protect.
- Add parser/package tests for rule interpretation changes and inspection fixture tests for editor behavior.

## Documentation and release contract

When changing rule behavior or user-visible messages, update the same slice:

- `docs/rule-support-matrix.md`
- `README.md` and `README.ko.md`
- inspection descriptions under `src/main/resources/inspectionDescriptions/`
- `CHANGELOG.md`

Release readiness requires a clean explanation of known limitations, the supported IntelliJ/JDK policy, Plugin Verifier status, and verification commands.

## Pull request checklist

Before opening a PR, record the commands that match your change scope:

- [ ] `./gradlew ktlintCheck`
- [ ] `./gradlew test`
- [ ] `./gradlew check`
- [ ] `./gradlew verifyPlugin` when claiming release/plugin compatibility
- [ ] Documentation diff review when support status, README, inspection descriptions, or release notes changed

Do not add external dependencies unless the change explicitly requires and justifies them.
