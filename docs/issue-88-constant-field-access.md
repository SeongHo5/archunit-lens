# Issue 88 constant field-access evidence

Exact `noClasses().should().accessField(Owner.class, "field")` warnings exclude reads of Java constant variables. `PsiField.computeConstantValue()` proves the initializer has a compile-time value, and `PsiUtil.isAccessedForWriting()` keeps explicit writes. This uses PSI only; the plugin does not compile or execute project code or load ArchUnit.

[JLS 4.12.4](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.12.4) defines constant variables as final primitive or String variables initialized with a constant expression. [JLS 13.1](https://docs.oracle.com/javase/specs/jls/se21/html/jls-13.html#jls-13.1) requires constant references to resolve to their values at compilation. This also covers non-static constant reads. Their instance initializer still writes the field during construction.

## Shared Java fixtures and independent oracle

`src/test/testData/codeaccess/constantFields` supplies the same Java inputs to the IntelliJ inspection fixtures and a separately compiled ArchUnit 1.4.1 oracle. No Gradle, plugin compile, or plugin runtime dependency is added. `oracle/ConstantFieldAccessOracle.java` runs direct ArchUnit evaluations and checks imported bytecode accesses.

With JDK 21 selected and standalone ArchUnit 1.4.1 and SLF4J API JAR paths supplied:

```sh
oracle_classes=$(mktemp -d)
javac -cp "$ARCHUNIT_ORACLE_JAR" -d "$oracle_classes" \
  src/test/testData/codeaccess/constantFields/*.java \
  src/test/testData/codeaccess/constantFields/oracle/ConstantFieldAccessOracle.java
java -cp "$oracle_classes:$ARCHUNIT_ORACLE_JAR:$SLF4J_API_JAR" \
  oracle.ConstantFieldAccessOracle
javap -c -p -classpath "$oracle_classes" com.example.constants.ConstantReads
```

| Fixture | ArchUnit 1.4.1 compiled oracle | Lens warning expectation |
| --- | --- | --- |
| `ConstantReads` | No field accesses; all 11 exact field bans pass | No warning for all primitive types, String, transitive constant expressions, static imports, fully qualified and instance-qualified reads |
| `RealReads` | Eight GET accesses; six exact field bans fail | Eight matching highlights for method-initialized static final primitive/String, object-valued fields initialized with a String literal, mutable, and blank-final fields, including static imports |
| `FieldWrites` | Three SET and two GET accesses; mutable field ban fails | Three highlights for simple assignment, compound assignment, and increment |
| `Constants` | Static and constructor blank-final assignments remain SET accesses | Both explicit initialization assignments remain highlighted |

The oracle also checks that the instance constant declaration initializer emits a SET and violates its exact field ban. Lens already scans reference expressions rather than synthesizing accesses from declaration initializers; such implicit declaration writes remain outside this fix. Removing inlined reads must not be mistaken for adding support for those implicit writes.

## Regression commands

```sh
./gradlew test --tests '*ArchUnitLensInspectionTest.testExactFieldAccess*' --no-daemon --max-workers=2
./gradlew ktlintCheck test check --no-daemon --max-workers=2
```

Validation uses Temurin JDK 21.0.11. The regression fixture tests inspect both diagnostic counts/ranges and resolved constant-access results. Existing exact owner/signature and resolution-count fixtures remain part of the full suite.
