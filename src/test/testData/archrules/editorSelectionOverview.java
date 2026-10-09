import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class ArchitectureRules {
    @ArchTest
    static final ArchRule package_a_rule =
            classes().that().resideInAPackage("com.example.a..")
                    .should().haveSimpleNameEndingWith("Service");

    @ArchTest
    static final ArchRule package_b_rule =
            classes().that().resideInAPackage("com.example.b..")
                    .should().haveSimpleNameEndingWith("Repository");
}
