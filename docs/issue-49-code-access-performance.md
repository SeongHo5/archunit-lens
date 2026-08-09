# Issue 49 exact code-access performance evidence

## Deterministic gate

The inspection indexes active exact field and method conditions by member name. A Java body callback returns before PSI resolution when its name is not present. Tests wrap real `PsiReferenceExpression` and `PsiMethodCallExpression` candidates and assert that the single matching field and method each resolve once while unrelated calls resolve zero times. Dumb mode resolves and reports nothing.

The representative fixture contains 1,000 unrelated `helper()` calls plus one `System.out` field access and one zero-argument `Throwable.printStackTrace()` call. One visitor pass therefore has 1,002 method-call callbacks and over 1,000 reference-expression callbacks, but exactly two member resolutions.

## Timing method

The visitor is built once against IntelliJ IDEA 2025.2.6.2 test fixtures. Three complete passes warm PSI and indexes, followed by ten measured passes. Gradle runs on Temurin 21.0.11 and the fixture executor runs on the bundled JBR 21.0.9. Timing is evidence rather than a threshold because host load and PSI caches vary; the enforced regression gate is the exact resolution count.

The final verification fixture observed 1,007 reference-expression callbacks and 1,002 method-call callbacks per pass. Only the `out` and `printStackTrace` candidates resolved, and the ten-pass median was 250,500 ns (about 0.25 ms) after warmup.

## Dynamic unload review

The registered inspection remains a Kotlin class with no companion object or extension-lifetime state. Candidate maps and the Java visitor are local to `buildVisitor`; the stateless top-level evaluator returns string-only value objects and retains no PSI, project, service, extension, or classloader-owned references. The change adds no listener, service, cache, or disposable resource.

Run the representative measurement with:

```sh
./gradlew test --tests '*ArchUnitLensInspectionTest.testCodeAccessPrefilterRepresentativeJavaBodyTiming' --info
```
