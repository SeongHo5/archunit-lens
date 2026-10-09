import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

class ArchitectureRules {
    @ArchTest static final ArchRule selected_classes = classes().that()
            .areMetaAnnotatedWith("com.example.Marker").should().beInterfaces();
    @ArchTest static final ArchRule selected_methods = methods().that()
            .areMetaAnnotatedWith("com.example.Marker").should().beStatic();
    @ArchTest static final ArchRule forbidden_fields = noFields().should()
            .beMetaAnnotatedWith("com.example.Marker");
    @ArchTest static final ArchRule forbidden_methods = noMethods().should()
            .beMetaAnnotatedWith("com.example.Marker");
}
