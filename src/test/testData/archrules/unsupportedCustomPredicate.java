import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class ArchitectureRules {
    @ArchTest
    static final ArchRule custom_proxy_helper_is_unsupported =
            classes()
                    .that()
                    .areInterfaces()
                    .should()
                    .notBeMetaAnnotatedWith(proxyAnnotations());
}
