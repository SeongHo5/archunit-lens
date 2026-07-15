import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureRules {
    @ArchTest
    static final ArchRule unresolved_forbidden_annotation = noClasses().that()
            .resideInAPackage("..domain..")
            .should().beAnnotatedWith(com.example.missing.Forbidden.class);

    @ArchTest
    static final ArchRule unresolved_annotation_exclusivity = classes().that()
            .areAnnotatedWith(com.example.missing.Required.class)
            .should().notBeAnnotatedWith(com.example.missing.Forbidden.class);

    @ArchTest
    static final ArchRule unresolved_interface_assignability = classes().that()
            .haveSimpleNameEndingWith("Mapper")
            .should().beInterfaces().andShould()
            .beAssignableTo(com.example.missing.Base.class);

    @ArchTest
    static final ArchRule unresolved_class_meta_annotation = classes().that().areInterfaces()
            .should().notBeMetaAnnotatedWith(com.example.missing.Proxy.class);

    @ArchTest
    static final ArchRule unresolved_method_meta_annotation = methods().that()
            .areDeclaredInClassesThat().areInterfaces()
            .should().notBeMetaAnnotatedWith(com.example.missing.Proxy.class);
}
