# Project Structure

## MUST

- Keep production Kotlin code under `src/main/kotlin/io/github/archunitlens`.
- Keep plugin descriptors and UI text under `src/main/resources`.
- Keep IntelliJ plugin descriptors under `src/main/resources/META-INF/plugin.xml`.
- Keep inspection descriptions under `src/main/resources/inspectionDescriptions`.
- Keep resource bundles under `src/main/resources/messages`.
- Mirror production package structure under `src/test/kotlin`.
- Keep test fixtures under `src/test/testData`.
- Keep planning notes under `docs/`.
- Keep CI and release automation under `.github/workflows/`.
