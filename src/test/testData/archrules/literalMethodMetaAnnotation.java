import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

class ArchitectureRules {
    @ArchTest
    static final ArchRule interface_methods_must_not_have_proxy_annotations =
            methods()
                    .that()
                    .areDeclaredInClassesThat()
                    .areInterfaces()
                    .should()
                    .notBeMetaAnnotatedWith("com.example.Proxy");
}
