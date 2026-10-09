import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

// CLASS_IGNORE
class MutableRules {
    // FIELD_IGNORE
    @ArchTest static final ArchRule mutable_rule = classes().should().beInterfaces();
    @ArchTest static final ArchRule active_sibling = classes().should().beInterfaces();
}
