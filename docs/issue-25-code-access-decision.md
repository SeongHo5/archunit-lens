# Issue #25: Code-access PSI decision

## Decision

**DEFER.** ArchUnit Lens keeps `callMethod(...)` and `accessField(...)` rules as Rule Overview metadata and does not register live warnings for method-body access yet.

The current inspection visits declarations and references needed by the supported rule families. Adding a recursive body scan without a narrower proof would put resolution work on a hot editor path and could report calls or fields with the same name but a different owner. This change therefore adds no body visitor, call graph, data-flow analysis, cache, setting, or dependency.

## Smallest safe future shapes

A later spike should start with exact resolved identities only:

1. `System.out` and `System.err` when a `PsiReferenceExpression.resolve()` result is the exact `java.lang.System` field.
2. An explicit `Throwable.printStackTrace()` call when `PsiMethodCallExpression.resolveMethod()` returns the exact zero-argument method on `java.lang.Throwable` or a resolved subtype.

Logger calls that accept a `Throwable` are a separate shape: overload resolution and argument position must be proven independently rather than inferred from a method name.

## PSI and platform boundary

- Candidate callbacks: a non-recursive `JavaElementVisitor` over `PsiMethodCallExpression` and `PsiReferenceExpression`.
- Candidate resolution APIs: `PsiMethodCallExpression.resolveMethod()` and `PsiReferenceExpression.resolve()` followed by exact owner FQN, member name, and signature checks.
- Prefilter by cheap reference/method names before resolving, and resolve each candidate once.
- Perform PSI reads only inside the inspection visitor/read-action contract. Do not retain raw PSI outside that lifetime.
- During indexing/dumb mode, return no new findings unless a later design proves a safe stub/index path.
- Any cache would need invalidation for PSI changes, classpath changes, and settings; no cache is justified by current measurements.
- Do not keep visitor, PSI, or classloader-owned state beyond the inspection lifecycle so dynamic unload remains safe.

These constraints follow the IntelliJ Platform guidance for [inspections](https://plugins.jetbrains.com/docs/intellij/code-inspections.html), [threading and read actions](https://plugins.jetbrains.com/docs/intellij/threading-model.html), [indexing and dumb mode](https://plugins.jetbrains.com/docs/intellij/indexing-and-psi-stubs.html), and [PSI performance](https://plugins.jetbrains.com/docs/intellij/psi-performance.html).

## False-positive boundary

No warning may be based only on source text, an unresolved reference, a matching method/field name, a helper method, a lambda, or a custom ArchUnit condition. Resolution failure means no warning. A supported sibling must not be reported when another sibling changes the code-access rule's meaning and is unsupported.

## Evidence required to revisit

Reconsider `DEFER` only when a focused spike supplies all of the following:

1. resolved positive and same-name/different-owner negative fixtures for each proposed shape;
2. unresolved and dumb-mode fixtures proving zero warnings;
3. inspection timing evidence on representative Java bodies showing name prefiltering keeps resolution bounded;
4. exact warning range/message tests and dynamic-unload review;
5. a fail-closed parser capability gate for the entire code-access rule, not a partial condition;
6. IntelliJ platform auditor approval.

Until then, parser and inspection regressions assert that `callMethod(...)` and `accessField(...)` remain metadata-only and produce no editor warning.
