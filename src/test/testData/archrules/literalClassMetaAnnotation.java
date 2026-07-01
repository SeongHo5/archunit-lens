import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class ArchitectureRules {
    @ArchTest
    static final ArchRule proxy_annotations_belong_on_concrete_classes =
            classes()
                    .that()
                    .areInterfaces()
                    .should()
                    .notBeMetaAnnotatedWith("com.example.Proxy");
}
