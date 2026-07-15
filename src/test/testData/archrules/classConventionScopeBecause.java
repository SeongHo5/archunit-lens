import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.example.in.scope")
class ArchitectureRules {
    @ArchTest
    static final ArchRule services_are_not_impl = classes().that().areNotEnums()
            .should().haveSimpleNameNotEndingWith("Impl")
            .because("Implementations stay behind ports.");
}
