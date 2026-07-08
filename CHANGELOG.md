# ArchUnit Lens Changelog

## [0.1.2] - 2026-07-08

Patch release focused on safer navigation, clearer overview filtering, and more precise unsupported-rule messaging.

### Fixed

- Lowered **Go to ArchUnit rule** quick-fix priority so direct code fixes stay easier to pick.
- Kept quick-fix navigation and Rule Overview formatting inside safe IntelliJ read boundaries.
- Widened the Rule Overview search field for long rule names.
- Clarified unsupported reasons for multi-package rule shapes.

## [0.1.1] - 2026-07-03

Patch release focused on Rule Overview refresh stability.

### Fixed

- Prevented Rule Overview refresh from reading PSI outside IntelliJ read actions.

## [0.1.0] - 2026-07-01

Initial public release.

### Added

- Live IntelliJ IDEA inspections for a conservative Java ArchUnit rule subset.
- Rule support for package dependency bans, class suffix rules, forbidden annotations, annotation exclusivity, QueryMapper-style interface rules, and literal class/method meta-annotation rules.
- Static handling for `@AnalyzeClasses(packages = ...)` scope and `.because("...")` reason text.
- Resolved Java dependency-reference diagnostics for imports, inheritance, fields, methods, constructors, inline FQNs, and wildcard-import-backed references.
- Rule Overview tool window with supported/unsupported rule metadata, source navigation, filters, scan metrics, and indexing/cache diagnostics.
- Settings UI for rule-family toggles, scan exclusions, overview visibility, diagnostics, and metrics logging.
- Safe quick fixes for rule navigation, class suffix rename, and forbidden annotation removal.
- English/Korean README, inspection descriptions, support matrix, CI, plugin verification workflow, and contribution guide.

### Known limitations

- ArchUnit Lens never executes ArchUnit rules, helper methods, lambdas, `DescribedPredicate`, `ArchCondition`, or project code.
- Method-style `@ArchTest` rules, Kotlin rule parsing, Kotlin target inspection, unresolved references, and arbitrary boolean/member DSL chains do not produce live warnings.
