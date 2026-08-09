import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureRules {
    @ArchTest
    static final ArchRule no_print_stack_trace = noClasses()
            .should().callMethod(java.lang.Throwable.class, "printStackTrace");

    @ArchTest
    static final ArchRule no_system_streams = noClasses()
            .should().accessField(java.lang.System.class, "out")
            .orShould().accessField(java.lang.System.class, "err");
}
