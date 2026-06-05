# ArchUnit Lens

ArchUnit Lens is an IntelliJ IDEA plugin MVP that shows a small, conservative subset of existing ArchUnit Java rules as live editor inspections.

## v0.1 scope

ArchUnit tests stay the source of truth. The plugin does not execute ArchUnit and does not try to interpret the full DSL. It statically reads Java PSI for:

```java
@ArchTest
static final ArchRule ruleName = ...;
```

Supported rule patterns:

1. Package dependency ban
   - `noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAPackage("..infrastructure..")`
   - Warns on clear forbidden `import` statements.
2. Class name suffix
   - `classes().that().resideInAPackage("..controller..").should().haveSimpleNameEndingWith("Controller")`
   - Warns on matching-package classes whose simple name misses the suffix.
3. Forbidden annotation
   - `noClasses().that().resideInAPackage("..domain..").should().beAnnotatedWith(Service.class)`
   - Warns on matching-package classes with the forbidden resolved annotation.

Each warning offers `Go to ArchUnit rule`. Where safe, warnings also offer focused edits such as appending the required class suffix or removing the forbidden annotation.

## Non-goals

v0.1 intentionally does not support:

- Full ArchUnit DSL execution or custom `ArchCondition` / `DescribedPredicate` interpretation.
- Method-style `@ArchTest` rules.
- Kotlin ArchUnit test parsing.
- `resideInAnyPackage(...)` chains, until the parser can support them consistently instead of partially.
- Wildcard imports, inline fully-qualified references, unused-import reasoning, or full dependency graph analysis.
- Tool window / project overview UI.
- Public release, marketplace, or supported-IDE-version decisions.
- New external dependencies without explicit approval.

## Verification

Use JDK 17 for the IntelliJ Platform test path:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat test
```

To inspect the target ArchUnit DSL locally without adding ArchUnit to the plugin classpath:

```powershell
.\gradlew.bat unpackArchUnitReferenceSources
```

The task unpacks sources for `archUnit.reference.version` into `build/reference-sources/archunit/<version>`.

The current test suite covers:

- product identity smoke test
- segment-aware package pattern matching
- supported/unsupported ArchUnit parser fixtures
- Java local-inspection highlighting for all three v0.1 rule patterns
- basic negative cases and safe quick-fix behavior

## Planning artifacts

- `docs/00-plan.md` — original deep-interview product plan
- `docs/01-1.0.0-milestone.md` — consolidated implementation notes, release roadmap, and extensible rule-engine design
- `.omx/plans/ralplan-archunit-lens-mvp.md` — implementation PRD/test plan
- `.omx/ultragoal/goals.json` and `.omx/ultragoal/ledger.jsonl` — durable implementation ledger
