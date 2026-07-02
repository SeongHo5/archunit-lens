# Build and Verification

## MUST

- Use the Gradle wrapper from the repository root.
- Use JDK 21 for Gradle builds, tests, plugin verification, and CI parity.
- Run `./gradlew test` for small code changes.
- Run `./gradlew ktlintCheck` to verify Kotlin formatting.
- Run `./gradlew ktlintFormat` only when formatting changes are needed.
- Run `./gradlew check` before pull requests or broad verification claims.
- Run `./gradlew buildPlugin` before validating plugin distribution packaging.
- Run `./gradlew verifyPlugin` before compatibility or release claims.
- Run `./gradlew unpackArchUnitReferenceSources` before local ArchUnit DSL source inspection.

## CONDITIONAL

IF running on a Windows shell:

- Use `gradlew.bat` instead of `./gradlew`.
