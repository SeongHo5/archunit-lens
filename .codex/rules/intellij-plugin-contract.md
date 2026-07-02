# IntelliJ Plugin Contract

## MUST

- Implement every `plugin.xml` registered extension as a Kotlin `class`.
- Keep extension `companion object` members limited to simple constants or a logger.
- Keep service constructors lightweight.
- Acquire services only where needed.
- Use `Disposable` cleanup for services, listeners, background activities, and plugin-lifetime resources.
- Wrap PSI, VFS, and project model reads in a read action outside EDT write-intent contexts.
- Wrap PSI, VFS, and project model writes in a write action from a write-safe EDT context.
- Validate PSI, VFS, project, and module objects across read-action boundaries.
- Keep expensive PSI access out of hot editor paths unless cached with correct dependencies.
- Cache heavy PSI computations with invalidation tied to every input dependency.
- Include settings changes in cache invalidation when settings affect computed results.
- Store `SmartPsiElementPointer` instead of long-lived PSI references.
- Keep dynamic plugin unload safe by avoiding plugin-lifetime references to PSI, extensions, and classloader-owned objects.
- Move extension state to top-level declarations, services, or non-registered utility objects.
- Move extension caches to top-level declarations, services, or non-registered utility objects.
- Move extension `Regex` values to top-level declarations, services, or non-registered utility objects.
- Move extension `TokenSet` values to top-level declarations, services, or non-registered utility objects.
- Move extension factories to top-level declarations, services, or non-registered utility objects.
- Move heavy extension helpers to top-level declarations, services, or non-registered utility objects.

## MUST NOT

- Do not register Kotlin `object` declarations as plugin extensions.
- Do not perform heavy initialization in service constructors.
- Do not store service instances in static fields or long-lived properties.
- Do not block EDT with long-running PSI scans, resolve loops, indexing, IO, or plugin verification work.
- Do not store raw PSI elements in objects that can survive plugin unload, project close, or file invalidation.
- Do not use `getText()` in hot PSI paths when a narrower text API is sufficient.
- Do not keep extension instances in long-lived collections for dynamic extension points.

## CONDITIONAL

IF adding a feature, refactoring plugin runtime behavior, changing inspections, quick fixes, tool windows, settings, caches, parser support, or touching `plugin.xml`:

- Run the `intellij-platform-auditor` agent before final report.
- Treat a `BLOCK` verdict as a failed gate.

IF changing cache keys, cache values, scan exclusions, settings, or PSI-derived discovery:

- Add or update regression coverage that fails when stale data is reused.
- Verify every cache dependency is represented in invalidation logic.

IF changing `plugin.xml` registrations:

- Verify every registered implementation is a Kotlin `class`.
- Verify dynamic plugin restrictions still hold.

IF changing listeners, services, background tasks, or tool windows:

- Verify cleanup is tied to a `Disposable` lifecycle.
- Verify plugin unload does not leave classloader-owned references alive.
