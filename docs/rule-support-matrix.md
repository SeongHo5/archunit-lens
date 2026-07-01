# ArchUnit Lens Rule Support Matrix

This is the canonical support reference for the current ArchUnit Lens implementation. README files, inspection descriptions, and release notes should summarize this file instead of duplicating long rule lists.

Legend:

- **Supported**: produces live editor warnings when static PSI evidence is sufficient.
- **Metadata only**: discovered and shown in the Rule Overview, but never registered as a live warning.
- **Unsupported**: intentionally outside the current static-analysis subset.
- **Planned**: accepted backlog item that still needs tests before it can be marked supported.

ArchUnit tests remain the source of truth. ArchUnit Lens never executes ArchUnit rules, project helper methods, lambdas, `DescribedPredicate`, or `ArchCondition` implementations.

| Rule family / DSL shape | Status | Live warning | Metadata retained | Static evidence / false-positive policy | Fixture status | Docs exposure |
| --- | --- | --- | --- | --- | --- | --- |
| `@ArchTest static final ArchRule` fields | Supported | Yes, when the rule family below is supported | Yes | Field initializer is parsed with PSI only. Method-style `@ArchTest` rules are not executed. | Parser and inspection fixtures | README, inspection description, overview |
| `@AnalyzeClasses(packages = ...)` scope | Supported | Scope gate only | Yes | Warnings are emitted only for Java files inside the declared package scope. | Parser and inspection fixtures | README, troubleshooting |
| `.because("...")` reason text | Supported | Message detail only | Yes | Literal reason text is copied into diagnostics and overview metadata. | Parser and inspection fixtures | README, overview |
| Package dependency ban: `noClasses().that().resideInAPackage(...).should().dependOnClassesThat().resideInAPackage(...)` | Supported | Yes, for explicit imports and resolved Java references | Yes | Target FQN must match the forbidden package pattern. Explicit import diagnostics are deduplicated against resolved reference diagnostics. | Parser and inspection fixtures | README, inspection description |
| Class suffix rule: `classes().that().resideInAPackage(...).should().haveSimpleNameEndingWith(...)` | Supported | Yes | Yes | Class package must match the source package pattern; quick fix appends the suffix through IntelliJ rename support. | Parser, inspection, quick-fix fixtures | README, inspection description |
| Forbidden annotation: `noClasses().that().resideInAPackage(...).should().beAnnotatedWith(SomeAnnotation.class)` | Supported | Yes | Yes | Annotation class literal must resolve or be statically qualified. Quick fix removes the forbidden annotation. | Parser, inspection, quick-fix fixtures | README, inspection description |
| Annotation exclusivity: `classes().that().areAnnotatedWith(...).should().notBeAnnotatedWith(...)` | Supported | Yes | Yes | Literal String/Class annotation arguments only. | Parser and inspection fixtures | README, inspection description |
| QueryMapper-style interface rule: `classes().that().haveSimpleNameEndingWith("QueryMapper").should().beInterfaces().andShould().beAssignableTo(QueryMapper.class)` | Supported subset | Yes, when assignable target resolves | Yes | If the `beAssignableTo` target cannot be resolved, Lens avoids a live warning. | Parser and inspection fixtures | README, inspection description |
| Literal class meta-annotation rule: `classes().that().areInterfaces().should().notBeMetaAnnotatedWith(...)` | Supported subset | Yes | Yes | Literal String/Class meta-annotation arguments only. Custom predicates remain metadata-only. | Parser and inspection fixtures | README, inspection description |
| Literal method meta-annotation rule: `methods().that().areDeclaredInClassesThat().areInterfaces().should().notBeMetaAnnotatedWith(...)` | Supported subset | Yes | Yes | Literal String/Class meta-annotation arguments only; broader method/member predicates are not live-evaluated. | Parser and inspection fixtures | README, inspection description |
| Bounded `and()` / `or()` predicate trees that do not match a supported family | Metadata only | No | Yes | Retained for overview because boolean live evaluation can create false positives when the full chain is not supported. | Parser metadata fixtures | Rule Overview, backlog |
| Package-aware rule lookup/cache, settings, and indexing policy | Supported | Settings can disable live rule families; lookup itself is not a warning | Yes | `rulesForPackage(...)` / `discoveriesForPackage(...)` filter by `@AnalyzeClasses` scope plus supported source-package predicates, cache package lookups per PSI modification count, honor configured path-fragment exclusions, and return the previous cache during dumb/indexing mode instead of querying indexes. | Service/settings fixture, dumb-mode fixture, performance smoke fixture | Settings UI, Rule Overview scan metrics, performance docs |
| `resideInAnyPackage(...)` as source predicate or dependency target for package dependency bans | Supported | Yes | Yes | Single-package and multi-package forms normalize to the same package-list model. | Parser and inspection fixtures | README, inspection description |
| Wildcard import dependency references | Supported subset | Yes, when the referenced class resolves | Yes | The wildcard import statement alone is not enough; a concrete resolved class reference must match the forbidden package. | Inspection fixture | README limitations |
| Extends/implements, field type, method return/parameter, constructor/new, inline FQN dependency references | Supported subset | Yes, when the target class resolves | Yes | Resolve failures create no live warning; duplicate import/reference diagnostics are deduplicated. | Inspection fixtures | README, inspection description |
| Method-style `@ArchTest` rules | Unsupported | No | No live metadata guarantee | Requires executing or modeling method bodies and is outside the Java field-rule MVP. | Unsupported fixture candidate | README limitations |
| Custom `DescribedPredicate`, custom `ArchCondition`, helper/lambda code | Unsupported / metadata only when discovered | No | Yes when discoverable as an unsupported chain | User/project code is never executed or partially interpreted for live warnings. | Unsupported metadata fixtures | README limitations, CONTRIBUTING |
| Kotlin ArchUnit rule sources or Kotlin target-source inspection | Unsupported | No | No | Java PSI MVP only. | Not applicable | README limitations |
| Layered/slices/onion helpers, cycle checks, separate Lens DSL, architecture refactor quick fixes | Backlog | No | Planned as overview/report candidates first | Project-wide graph or custom helper semantics are required; live warnings need separate false-positive gates. | Backlog | Backlog |
