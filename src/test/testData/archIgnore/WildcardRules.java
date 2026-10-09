import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.junit.ArchTest;
import com.example.fake.*;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class WildcardRules {
    @ArchIgnore
    @ArchTest static final ArchRule wildcard_rule = classes().should().beInterfaces();
}
