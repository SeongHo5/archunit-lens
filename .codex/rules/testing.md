# Testing

## MUST

- Use JUnit 4 for tests.
- Use IntelliJ Platform test fixtures for editor behavior.
- Add parser tests for rule interpretation changes.
- Add package tests for rule interpretation changes.
- Add inspection fixture tests for editor behavior changes.
- Put Java fixture inputs in `src/test/testData`.
- Name test scenarios after the rule or quick fix.

## CONDITIONAL

IF changing only a small behavior:

- Run `./gradlew test`.

IF preparing a pull request:

- Run `./gradlew check`.
