import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.allowed.domain.ScopeAnchor;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses
class ArchitectureRules {
    static final String ROOT = "com.allowed";
    static String MUTABLE = "com.allowed";

    @ArchTest
    static final ArchRule scope_rule = classes().should().beInterfaces()
            .because("Import scope must be preserved.");
}
