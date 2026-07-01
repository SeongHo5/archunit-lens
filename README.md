# ArchUnit Lens

[![CI](https://github.com/archunit-lens/archunit-lens/actions/workflows/ci.yml/badge.svg)](https://github.com/archunit-lens/archunit-lens/actions/workflows/ci.yml)
[한국어](README.ko.md)

**See supported ArchUnit violations while coding.** ArchUnit Lens is an IntelliJ IDEA plugin that turns a conservative Java ArchUnit rule subset into live editor inspections. It gives early feedback, but ArchUnit tests remain the source of truth.

## First five minutes

1. Install the plugin or run it with `runIde`.
2. Open a Java project that already has `@ArchTest static final ArchRule` fields.
3. Edit a Java file inside the rule's `@AnalyzeClasses(packages = ...)` scope.
4. Review editor warnings and safe quick fixes.
5. Open **ArchUnit Lens** from the right tool window bar to inspect discovered rules.

```java
@ArchTest
static final ArchRule mapperAnnotationMustBeExclusive =
    classes()
        .that()
        .areAnnotatedWith("org.mapstruct.Mapper")
        .should()
        .notBeAnnotatedWith("com.example.SecondaryMapper")
        .because("Mapper annotations must be exclusive.");
```

## 0.1.0 support scope

ArchUnit Lens supports only statically provable Java rule-field patterns. Unsupported or ambiguous DSL chains are retained as Rule Overview metadata when possible and never produce live warnings. The canonical support reference is [`docs/rule-support-matrix.md`](docs/rule-support-matrix.md).

The initial live-warning subset includes:

- package dependency bans for `resideInAPackage(...)` / `resideInAnyPackage(...)`, explicit imports, and resolved Java references
- class suffix rules
- forbidden annotations
- annotation exclusivity
- QueryMapper-style interface rules with resolvable `beAssignableTo(...)`
- literal class and method meta-annotation rules
- `@AnalyzeClasses(packages = ...)` scope and `.because("...")` reason text

## Rule Overview

Open **ArchUnit Lens** from the right tool window bar to review discovered rules. The overview shows supported and unsupported rule fields with filters, rule-name search, source navigation, current-file-only scope, subject/unsupported-reason grouping, reason text, scan metrics, package cache metrics, and indexing/stale fallback diagnostics.

## Quick fixes

Every warning includes **Go to ArchUnit rule**. Some violations also include a direct fix:

- Missing class suffix -> rename the class by appending the required suffix.
- Forbidden annotation / `notBeAnnotatedWith(...)` violation -> remove the forbidden annotation.

Dependency violations stay navigation/explanation-only; the plugin does not rewrite imports or architecture dependencies automatically.

## Known limitations

ArchUnit Lens does not execute ArchUnit rules or user/project code. It intentionally does not support live warnings for:

- custom `ArchCondition`, `DescribedPredicate`, helper methods, or lambdas
- method-style `@ArchTest` rules
- Kotlin ArchUnit rule parsing or Kotlin target-source inspection
- unused wildcard import statements without a resolved referenced class
- unresolved dependency references; resolve failures intentionally produce no warning
- arbitrary method/member subject rules beyond the literal meta-annotation subset
- arbitrary boolean predicate/condition tree evaluation

## Settings

Use **Settings | Tools | ArchUnit Lens** for per-rule-family enable/disable, overview visibility, scan exclusions, diagnostics, and metrics logging. Inspection severity remains controlled by IntelliJ inspection profiles.

## Compatibility and local development

- Plugin version: `0.1.0`
- Local IntelliJ Platform tests: use JDK 17.
- GitHub Actions CI: uses JDK 21.
- ArchUnit reference sources are pinned by `archUnit.reference.version` in `gradle.properties` and unpacked only for local DSL analysis; ArchUnit is not on the plugin compile/runtime classpath.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat runIde
```

Verification commands:

```powershell
.\gradlew.bat ktlintCheck
.\gradlew.bat test
.\gradlew.bat check
.\gradlew.bat buildPlugin
.\gradlew.bat verifyPlugin
```

For contribution rules and fixture conventions, see [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Troubleshooting

If a warning does not appear:

1. Confirm the rule is an `@ArchTest static final ArchRule` field.
2. Confirm the edited Java file is inside `@AnalyzeClasses(packages = ...)`, if that annotation is present.
3. Check the rule shape in the [support matrix](docs/rule-support-matrix.md).
4. Open the **ArchUnit Lens** tool window and look for unsupported metadata or scan metrics.
5. In `runIde`, open the IDE log and search for `ArchUnit Lens scan completed`.
