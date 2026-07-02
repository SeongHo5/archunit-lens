# ArchUnit Support Scope

## MUST

- Treat ArchUnit tests as the source of truth.
- Recognize only a conservative Java ArchUnit subset statically.
- Use `archUnit.reference.version` for local ArchUnit source inspection.
- Use `unpackArchUnitReferenceSources` for local ArchUnit source inspection.
- Keep ArchUnit reference sources off plugin compile classpaths.
- Keep ArchUnit reference sources off plugin runtime classpaths.

## MUST NOT

- Do not execute ArchUnit from the plugin.
- Do not partially support unsupported DSL chains without tests.
