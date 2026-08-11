import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class SignatureCodeAccessPerformanceRules {
    @ArchTest
    static final ArchRule no_system_out = noClasses().should()
            .accessField(java.lang.System.class, "out");

    @ArchTest
    static final ArchRule no_print_stack_trace = noClasses().should()
            .callMethod(java.lang.Throwable.class, "printStackTrace");

    @ArchTest
    static final ArchRule no_page_impl_constructor = noClasses().should()
            .callConstructor(org.springframework.data.domain.PageImpl.class, java.util.List.class);
}
