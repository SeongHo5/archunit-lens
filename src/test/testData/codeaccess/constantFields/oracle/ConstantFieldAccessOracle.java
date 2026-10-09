package oracle;

import com.example.constants.Constants;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Independent bytecode oracle; compiled manually, never loaded by the plugin. */
public class ConstantFieldAccessOracle {
    private static final List<String> CONSTANTS = List.of(
            "BOOLEAN", "BYTE", "SHORT", "CHAR", "INT", "LONG", "FLOAT", "DOUBLE",
            "TEXT", "EXPRESSION", "INSTANCE");
    private static final List<String> REAL_FIELDS = List.of(
            "RUNTIME", "RUNTIME_TEXT", "OBJECT", "MUTABLE", "INITIALIZED", "instanceInitialized");

    public static void main(String[] args) throws Exception {
        JavaClasses constants = importFixture("ConstantReads");
        for (String field : CONSTANTS) {
            assertRule(constants, field, false);
        }
        require(constants.get("com.example.constants.ConstantReads").getFieldAccessesFromSelf().isEmpty(),
                "Constant reads must not emit field bytecode");
        System.out.println("ConstantReads: no field accesses; all 11 exact field bans pass");

        JavaClasses reads = importFixture("RealReads");
        for (String field : REAL_FIELDS) {
            assertRule(reads, field, true);
        }
        require(reads.get("com.example.constants.RealReads").getFieldAccessesFromSelf().size() == 8,
                "Expected eight real reads, including static imports");
        System.out.println("RealReads: 8 GET accesses; all 6 exact field bans fail");

        JavaClasses writes = importFixture("FieldWrites");
        assertRule(writes, "MUTABLE", true);
        var accesses = writes.get("com.example.constants.FieldWrites").getFieldAccessesFromSelf();
        require(accesses.stream().filter(a -> a.getAccessType() == JavaFieldAccess.AccessType.SET).count() == 3,
                "Expected simple, compound and increment writes");
        require(accesses.stream().filter(a -> a.getAccessType() == JavaFieldAccess.AccessType.GET).count() == 2,
                "Compound assignment and increment must also read");
        System.out.println("FieldWrites: 3 SET + 2 GET accesses; MUTABLE exact field ban fails");

        JavaClasses initialization = new ClassFileImporter().importClasses(Constants.class);
        assertRule(initialization, "INITIALIZED", true);
        assertRule(initialization, "instanceInitialized", true);
        assertRule(initialization, "INSTANCE", true);
        System.out.println("Constants: blank-final static/constructor SET accesses preserved; " +
                "INSTANCE declaration initializer also emits SET (existing Lens declaration limitation)");
    }

    private static JavaClasses importFixture(String simpleName) throws Exception {
        return new ClassFileImporter().importClasses(Class.forName("com.example.constants." + simpleName));
    }

    private static void assertRule(JavaClasses classes, String field, boolean violation) {
        boolean actual = noClasses().should().accessField(Constants.class, field).evaluate(classes).hasViolation();
        require(actual == violation, field + ": expected violation=" + violation + ", actual=" + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
