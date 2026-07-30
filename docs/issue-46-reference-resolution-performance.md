# Issue 46 reference-resolution performance evidence

## Result

When a package has supported class rules but no active package-dependency rule,
the Java-reference visitor no longer calls `PsiJavaCodeReferenceElement.resolve()`.
The regression test wraps real PSI references with a resolve counter and asserts
that the count stays at zero.

## Representative timing

The measurement used IntelliJ IDEA 2025.2.6.2 test fixtures on Apple silicon.
Gradle ran on Temurin 21.0.11 and the fixture process ran on JetBrains Runtime
21.0.9. The fixture contained one supported class naming rule, no dependency
rule, and a Java class with 1,000 `java.util.List<String>` fields. This produced
4,003 `PsiJavaCodeReferenceElement` instances.

The visitor was built once, warmed up with three complete passes, and then timed
for ten complete passes. The table reports the median wall-clock time per pass.

| Implementation | Median per 4,003 references |
| --- | ---: |
| Before: resolve every non-import reference | 2.219 ms |
| After: return when `dependencyRules` is empty | 0.150 ms |

This representative hot-cache run reduced reference-visitor time by about
93.2%. Real project results depend on indexes and reference shapes, so timing is
recorded as evidence rather than enforced as a test threshold. The deterministic
gate is the zero-resolution regression test.
