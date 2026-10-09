import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class FieldRules {
    @ArchIgnore(reason = "disabled field")
    @ArchTest static final ArchRule ignored_field = classes().that()
            .resideInAPackage("..ignoredfield..").should().beInterfaces();
    @ArchTest static final ArchRule active_sibling = classes().that()
            .resideInAPackage("..active..").should().beInterfaces();
}

@ArchIgnore(reason = "disabled class")
class IgnoredClassRules {
    @ArchTest static final ArchRule ignored_class = classes().that()
            .resideInAPackage("..ignoredclass..").should().beInterfaces();

    static class IndependentNestedRules {
        @ArchTest static final ArchRule active_nested = classes().that()
                .resideInAPackage("..nested..").should().beInterfaces();
    }
}

class NestedRules {
    @com.tngtech.archunit.junit.ArchIgnore
    static class IgnoredNestedRules {
        @ArchTest static final ArchRule ignored_nested = classes().should().beInterfaces();
    }
}

class FullyQualifiedRules {
    @com.tngtech.archunit.junit.ArchIgnore
    @ArchTest static final ArchRule ignored_qualified_field = classes().should().beInterfaces();
}
