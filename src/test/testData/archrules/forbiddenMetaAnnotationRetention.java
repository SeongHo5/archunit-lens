import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

class ArchitectureRules {
    @ArchTest static final ArchRule forbidden_class_meta_annotations = classes().that()
            .areInterfaces().should().notBeMetaAnnotatedWith("com.example.Marker");
    @ArchTest static final ArchRule forbidden_method_meta_annotations = methods().that()
            .areDeclaredInClassesThat().areInterfaces().should().notBeMetaAnnotatedWith("com.example.Marker");
}
