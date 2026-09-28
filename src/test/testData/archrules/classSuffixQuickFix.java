import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class ArchitectureRules {
    @ArchTest
    static final ArchRule example_classes_should_end_with_service =
            classes().that().resideInAPackage("..example..")
                    .should().haveSimpleNameEndingWith("Service");
}
