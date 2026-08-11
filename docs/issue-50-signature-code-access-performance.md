# Issue 50 signature-aware code-access performance evidence

## Deterministic gate

The inspection indexes field and method leaves by member name and constructor leaves by owner simple name. Each ordinary visitor callback returns before PSI resolution when the prefilter misses. The regression fixture wraps real field, method, and constructor candidates and proves that each matching candidate resolves exactly once while unrelated names and constructed classes resolve zero times.

Java source classes with no declared constructors are the bounded exception to the normal constructor accounting. IntelliJ returns `null` from `resolveConstructor()` for their implicit zero-argument constructor, so only a prefiltered zero-argument candidate whose member resolve returned `null` performs one additional class-reference resolve. That fallback must produce an accessible, valid `PsiClass` with no declared constructors; normal resolved constructors perform no class fallback. Resolve-count tests enforce one member plus zero class resolves for a normal constructor and one member plus one class resolve for an implicit default constructor.

The representative Java body contains 1,000 unrelated `helper()` calls, 1,000 unrelated `new Object()` expressions, one exact `System.out` access, one exact `Throwable.printStackTrace()` call, and one exact `new PageImpl(List)` expression. A pass therefore observes 1,008 reference expressions, 1,002 method calls, and 1,001 constructions, but performs exactly three candidate resolutions.

## Timing method

The visitor is built once against the IntelliJ IDEA 2025.2.6.2 test fixture. Three complete passes warm PSI and indexes, followed by ten measured passes. Gradle uses Temurin 21; timing is evidence rather than a threshold because host load and PSI caches vary. The enforced regression gate is the exact resolution count.

The issue #50 verification run recorded a ten-pass median of 549,708 ns (about 0.55 ms) after warmup for all 2,003 unrelated method/construction candidates plus the three exact candidates. The implicit-default fallback does not affect unrelated constructions or constructors that resolve normally; it adds at most one class-reference resolve to a prefiltered zero-argument construction after its normal member resolve returns `null`.

Run the measurement with:

```sh
./gradlew test --tests '*ArchUnitLensInspectionTest.testCodeAccessPrefilterRepresentativeJavaBodyTiming' --info
```

## Dynamic unload review

The inspection remains a Kotlin class with no companion object or extension-lifetime state. Candidate maps, boolean evidence, raw PSI observations, and the reporter are local to one `buildVisitor` invocation. The evaluator returns string-only value objects and retains no PSI, project, service, extension, or classloader-owned references. Issue #50 adds no listener, service, cache, setting, dependency, or disposable resource.
