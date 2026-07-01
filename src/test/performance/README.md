# IntelliJ performance smoke test

Run the smoke test with:

```shell
./gradlew testIdePerformance
```

The `smoke.ijperf` script opens `smoke-java-project`, waits for indexing and smart mode,
forces ArchUnit Lens rule discovery over a small Java project with real ArchUnit rule sources,
emits discovery/cache metrics to `idea.log`, emits the `ArchUnit Lens Performance Smoke | Total Time Execution`
TeamCity metric, and exits the IDE. The gate is intentionally coarse-grained: the fixture must
complete within `180s` so the task detects startup/indexing regressions without turning a local
smoke check into a tight benchmark.
