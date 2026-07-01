import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureRules {
    @ArchTest
    static final ArchRule application_should_not_depend_on_adapters =
            noClasses()
                    .that()
                    .resideInAnyPackage("..application..", "..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..adapter..", "..infrastructure..");
}
