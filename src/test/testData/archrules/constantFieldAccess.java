import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.example.constants.Constants;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureRules {
    @ArchTest static final ArchRule no_BOOLEAN = noClasses().should().accessField(Constants.class, "BOOLEAN");
    @ArchTest static final ArchRule no_BYTE = noClasses().should().accessField(Constants.class, "BYTE");
    @ArchTest static final ArchRule no_SHORT = noClasses().should().accessField(Constants.class, "SHORT");
    @ArchTest static final ArchRule no_CHAR = noClasses().should().accessField(Constants.class, "CHAR");
    @ArchTest static final ArchRule no_INT = noClasses().should().accessField(Constants.class, "INT");
    @ArchTest static final ArchRule no_LONG = noClasses().should().accessField(Constants.class, "LONG");
    @ArchTest static final ArchRule no_FLOAT = noClasses().should().accessField(Constants.class, "FLOAT");
    @ArchTest static final ArchRule no_DOUBLE = noClasses().should().accessField(Constants.class, "DOUBLE");
    @ArchTest static final ArchRule no_TEXT = noClasses().should().accessField(Constants.class, "TEXT");
    @ArchTest static final ArchRule no_EXPRESSION = noClasses().should().accessField(Constants.class, "EXPRESSION");
    @ArchTest static final ArchRule no_INSTANCE = noClasses().should().accessField(Constants.class, "INSTANCE");
    @ArchTest static final ArchRule no_RUNTIME = noClasses().should().accessField(Constants.class, "RUNTIME");
    @ArchTest static final ArchRule no_RUNTIME_TEXT = noClasses().should().accessField(Constants.class, "RUNTIME_TEXT");
    @ArchTest static final ArchRule no_OBJECT = noClasses().should().accessField(Constants.class, "OBJECT");
    @ArchTest static final ArchRule no_MUTABLE = noClasses().should().accessField(Constants.class, "MUTABLE");
    @ArchTest static final ArchRule no_INITIALIZED = noClasses().should().accessField(Constants.class, "INITIALIZED");
    @ArchTest static final ArchRule no_instanceInitialized = noClasses().should().accessField(Constants.class, "instanceInitialized");
}
