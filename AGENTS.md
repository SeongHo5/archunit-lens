# Repository Guidelines

## Project Structure & Module Organization
ArchUnit Lens is a Kotlin IntelliJ IDEA plugin. Production code lives in `src/main/kotlin/io/github/archunitlens`, split into plugin entry points, `inspections/`, `inspections/quickfix/`, and `rules/`. Plugin descriptors and UI text live in `src/main/resources`, especially `META-INF/plugin.xml`, `inspectionDescriptions/`, and `messages/`. Tests mirror the package structure under `src/test/kotlin`; fixtures belong in `src/test/testData`. Planning notes are in `docs/`; CI and release automation are in `.github/workflows/`.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repository root.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat test          # run Kotlin/JUnit and IntelliJ Platform tests
.\gradlew.bat ktlintCheck   # verify Kotlin formatting
.\gradlew.bat ktlintFormat  # apply ktlint formatting
.\gradlew.bat check         # run full verification used by CI tests
.\gradlew.bat buildPlugin   # create build/distributions plugin ZIP
.\gradlew.bat verifyPlugin  # run IntelliJ Plugin Verifier tasks
.\gradlew.bat unpackArchUnitReferenceSources # unpack local ArchUnit DSL source reference under build/
```

CI uses JDK 21, but README recommends JDK 17 for local IntelliJ Platform tests.

## Coding Style & Naming Conventions
Write Kotlin with four-space indentation and idiomatic IntelliJ Platform APIs. Keep files/classes in PascalCase (`ArchRuleParser.kt`) and packages lowercase under `io.github.archunitlens`. Tests end in `Test`. Use KDoc for public plugin classes, services, rule models, and quick fixes so open-source readers can understand intent without tracing all PSI code. Keep parser/rule models in `rules/`; diagnostics and quick fixes in `inspections/`. Avoid new external dependencies unless explicitly approved.

## IntelliJ Plugin Implementation Rules
Anything registered in `plugin.xml` must be implemented as a Kotlin `class`, not `object`; the platform owns extension lifecycle and instantiation. In extension classes, `companion object` may contain only simple constants or a logger. Move other state, caches, Regex/TokenSet, factories, or heavy helpers to top-level declarations, services, or non-registered utility objects. Current utility `object`s are acceptable only while they are not registered as plugin extensions.

## Testing Guidelines
The suite uses JUnit 4 and IntelliJ Platform test fixtures. Add parser/package tests for rule interpretation changes and inspection fixture tests for editor behavior. Put Java fixture inputs in `src/test/testData`; name scenarios after the rule or quick fix. Run `test` before small changes and `check` before PRs.

## Commit & Pull Request Guidelines
Use short, descriptive, outcome-focused subjects like `Init ArchUnit Lens project`. PRs should describe behavior, list verification commands, link issues, and include screenshots/GIFs only for UI-visible changes.

## Architecture & Scope Notes
ArchUnit tests remain the source of truth. The plugin statically recognizes a conservative Java ArchUnit subset and must not execute ArchUnit or partially support unsupported DSL chains without tests.
Use `archUnit.reference.version` and `unpackArchUnitReferenceSources` for local ArchUnit source inspection; this reference source must stay off plugin compile/runtime classpaths.
