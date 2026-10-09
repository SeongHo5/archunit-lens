import com.example.fake.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@ArchIgnore
class UnrelatedRules {
    @ArchIgnore
    @ArchTest static final ArchRule active_unrelated = classes().should().beInterfaces();
}

class ShadowedRules {
    @interface ArchIgnore {}

    @ArchIgnore
    @ArchTest static final ArchRule active_shadowed = classes().should().beInterfaces();
}
