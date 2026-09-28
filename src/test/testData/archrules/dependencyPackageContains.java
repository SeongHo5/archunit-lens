import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureRules {
    @ArchTest
    static final ArchRule example_classes_should_not_depend_on_forbidden_package_segment =
            noClasses().that().resideInAPackage("..example..")
                    .should().dependOnClassesThat().resideInAPackage("..forbidden..");
}
