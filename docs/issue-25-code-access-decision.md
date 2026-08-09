# Issue #25: Code-access PSI decision

## Decision

**SUPERSEDED FOR THE EXACT SUBSET.** Issues #49 and #50 implement resolved exact field, signature-aware method, and constructor accesses for `noClasses()` rules. Access `Where` predicates, non-literal schemas, rule-side helper/custom lambdas, method references, call graphs, and bytecode analysis remain deferred.

The inspection uses ordinary non-recursive Java PSI visitor callbacks and resolves only candidate member names from a complete supported rule. Issue #49 adds no explicit recursive body scan, call graph, data-flow analysis, cache, listener, setting, or dependency.

## Implemented exact shapes

Issues #49 and #50 implement these resolved identities only:

1. `System.out` and `System.err` when a `PsiReferenceExpression.resolve()` result is the exact `java.lang.System` field.
2. A method call when `PsiMethodCallExpression.resolveMethod()` proves its name and ordered erased declaration parameter types, and the symbolic target owner exactly matches. Primitive, array, and vararg declaration types retain their raw JVM form.
3. An ordinary `new` or explicit `this`/`super` constructor call when exactly one constructor resolve proves the ordered erased declaration parameter types and symbolic constructed/delegated owner. Anonymous construction is excluded.

Logger calls that accept a `Throwable` are matched by resolved overload and argument position rather than inferred from a method name. Representative `PageImpl(List)` and `PageImpl(List, Pageable, long)` construction uses the same exact rule.

## PSI and platform boundary

- Candidate callbacks: a non-recursive `JavaElementVisitor` over `PsiMethodCallExpression`, `PsiNewExpression`, and `PsiReferenceExpression`.
- Candidate resolution APIs: `PsiMethodCallExpression.resolveMethod()`, `PsiNewExpression.resolveConstructor()`, and `PsiReferenceExpression.resolve()` followed by exact owner FQN, member name, and ordered raw-signature checks.
- Prefilter by cheap reference/method names before resolving, and resolve each candidate once.
- Perform PSI reads only inside the inspection visitor/read-action contract. Do not retain raw PSI outside that lifetime.
- During indexing/dumb mode, return no new findings unless a later design proves a safe stub/index path.
- Any cache would need invalidation for PSI changes, classpath changes, and settings; no cache is justified by current measurements.
- Do not keep visitor, PSI, or classloader-owned state beyond the inspection lifecycle so dynamic unload remains safe.

These constraints follow the IntelliJ Platform guidance for [inspections](https://plugins.jetbrains.com/docs/intellij/code-inspections.html), [threading and read actions](https://plugins.jetbrains.com/docs/intellij/threading-model.html), [indexing and dumb mode](https://plugins.jetbrains.com/docs/intellij/indexing-and-psi-stubs.html), and [PSI performance](https://plugins.jetbrains.com/docs/intellij/psi-performance.html).

## False-positive boundary

No warning may be based only on source text, an unresolved reference, a matching method/field name, a rule-side helper or lambda, or a custom ArchUnit condition. Resolution failure means no warning. A supported sibling must not be reported when another sibling changes the code-access rule's meaning and is unsupported. Ordinary accesses inside target-class lambda bodies remain part of the class-root inspection.

## Evidence gate

The original `DEFER` decision required a focused spike to supply all of the following:

1. resolved positive and same-name/different-owner negative fixtures for each proposed shape;
2. unresolved and dumb-mode fixtures proving zero warnings;
3. inspection timing evidence on representative Java bodies showing name prefiltering keeps resolution bounded;
4. exact warning range/message tests and dynamic-unload review;
5. a fail-closed parser capability gate for the entire code-access rule, not a partial condition;
6. IntelliJ platform auditor approval.

Issues #49 and #50 satisfy this gate with parser/inspection identity fixtures, deterministic resolve-count checks, representative timing in [`issue-50-signature-code-access-performance.md`](issue-50-signature-code-access-performance.md), fail-closed sibling parsing, exact diagnostics, and IntelliJ Platform audit. Shapes outside that exact subset remain metadata-only.
