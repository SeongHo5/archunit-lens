import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.example.RequestMapping;
import com.example.RestController;
import com.example.ResponseEntity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

class ArchitectureRules {
    @ArchTest
    static final ArchRule utility_constructors_are_private = constructors().that()
            .areDeclaredInClassesThat().resideInAPackage("..util..")
            .should().bePrivate();

    @ArchTest
    static final ArchRule utility_methods_are_static = methods().that()
            .areDeclaredInClassesThat().resideInAPackage("..util..")
            .should().beStatic();

    @ArchTest
    static final ArchRule controller_mappings_return_response_entity = methods().that()
            .areMetaAnnotatedWith(RequestMapping.class)
            .and().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .should().haveRawReturnType(ResponseEntity.class);
}
