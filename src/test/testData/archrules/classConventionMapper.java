import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class ArchitectureRules {
    @ArchTest
    static final ArchRule mapper_convention = classes().that()
            .resideInAPackage("..mapper..")
            .should().beInterfaces()
            .andShould().haveSimpleNameEndingWith("Mapper")
            .andShould().beAnnotatedWith("com.example.Mapper");
}
