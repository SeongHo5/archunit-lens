# ArchUnit Lens Changelog

## [Unreleased]

### Fixed

- Refreshed active editor diagnostics and existing Rule Overview panels when settings are applied, and synchronized overview filters without restoring stale preferences.
- Excluded rules disabled by ArchUnit `@ArchIgnore` on fields or declaring test classes from live warnings and Rule Overview, refreshing cached discovery after ignore edits and annotation resolution changes.
- Excluded SOURCE-retained applied and intermediate annotations from live class/member meta-annotation facts and removal fixes, preserving CLASS/RUNTIME and default CLASS paths.
- Included enclosing-instance parameters in exact source inner-constructor signatures across construction and delegation calls, and suppressed unprovable compiled inner signatures.
- Preserved `@AnalyzeClasses` package scopes for Java constants, mixed arrays, resolved `packagesOf`, and default test-class packages. Unresolved/custom import scopes remain metadata-only instead of producing global or partial warnings; external constant edits refresh scope caches.
- Required uniquely resolved ArchUnit entry-point ownership before enabling live rules, keeping same-name project helpers and unresolved or ambiguous roots metadata-only.
- Excluded inlined primitive/String constant field reads from exact access warnings while preserving nonconstant reads and explicit writes.

## [0.3.1] - 2026-09-29

Patch release focused on accurate package-rule feedback, fresh rule discoveries, and safe rename previews.

### Fixed

- Kept unsupported package patterns metadata-only across dependency, class suffix, and forbidden-annotation rules, including mixed pattern lists.
- Matched dependency targets by their actual package, including nested classes, while preserving import/reference deduplication and avoiding dependency resolution during indexing.
- Refreshed cached rules and overview data after source edits with identical text hashes while retaining unchanged-file reuse.
- Disabled unsupported class-suffix rename previews without changing the actual rename, usage updates, or undo behavior.

## [0.3.0] - 2026-08-11

Minor release focused on live member conventions and exact signature-aware code-access feedback.

### Added

- Expanded statically provable class conventions with record, final-modifier, composed meta-annotation, and safe literal package-array facts.
- Added live positive method/constructor conventions and negative field/method conventions for supported annotations, names, modifiers, and declaring-class facts.
- Added exact `noClasses()` field, method, and constructor access checks with overload-aware erased signatures, primitive/array/vararg parameters, explicit and implicit constructor calls, and bounded `andShould()` / `orShould()` chains.

### Fixed

- Kept unresolved, indexing-mode, dynamic, helper-backed, and mixed unsupported rule shapes warning-free while bounding editor-path PSI resolution.

## [0.2.1] - 2026-07-31

Patch release focused on accurate meta-annotation warnings, clearer custom-condition metadata, and leaner editor inspections.

### Fixed

- Matched direct and transitively composed meta-annotations with cycle-safe traversal while keeping unresolved paths warning-free.
- Reported helper-backed custom `ArchCondition` rules with stable metadata, the helper condition name, and separate `.because(...)` reason text without enabling live warnings.
- Skipped Java reference resolution when no enabled package-dependency rule applies, reducing unnecessary editor hot-path work.

## [0.2.0] - 2026-07-16

Minor release focused on live editor feedback for statically provable Java class conventions.

### Added

- Added live Java class conventions for annotation, package, suffix, interface, enum, and resolvable assignability leaves.
- Added left-associative class predicate `and()` / `or()` and independent `andShould()` condition evaluation when the full tree is statically supported.
- Added Spring, MapStruct, and MyBatis convention coverage plus metadata-only proof for deferred method/constructor and code-access rules.

### Fixed

- Preserved receiver-only ArchUnit call chains and ordered argument kinds so dynamic or helper-backed arguments cannot be partially interpreted.

## [0.1.3] - 2026-07-08

Patch release that republishes the 0.1.2 plugin updates under a new immutable Marketplace version.

### Fixed

- Kept the published plugin behavior aligned with 0.1.2 after the release pipeline retry required a new Marketplace version.

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
