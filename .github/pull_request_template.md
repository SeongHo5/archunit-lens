## Summary

-

## Verification

- [ ] `./gradlew ktlintCheck --no-daemon`
- [ ] `./gradlew test --no-daemon`
- [ ] `./gradlew check --no-daemon`
- [ ] `./gradlew verifyPlugin --no-daemon` (release/plugin compatibility changes only)
- [ ] Documentation diff review (support matrix, README, inspection descriptions, changelog)

## Rule-support impact

- [ ] No rule-support behavior changed
- [ ] `docs/rule-support-matrix.md` updated
- [ ] Unsupported/custom/helper code remains metadata-only and emits no live warnings
