import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class ArchitectureRules {
    @ArchTest
    static final ArchRule no_print_stack_trace = classes()
            .should().callMethod(Throwable.class, "printStackTrace");

    @ArchTest
    static final ArchRule no_system_out = classes()
            .should().accessField(System.class, "out");
}
