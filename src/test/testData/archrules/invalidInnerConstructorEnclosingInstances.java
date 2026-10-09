package com.example;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
class ArchitectureRules {
    @ArchTest static final ArchRule invalid_inner_calls = noClasses().should()
            .callConstructor(org.example.Outer.ImplicitInner.class, org.example.Outer.class)
            .orShould().callConstructor(org.example.Outer.ExplicitInner.class, org.example.Outer.class)
            .orShould().callConstructor(org.example.Outer.ExplicitInner.class,
                    org.example.Outer.class, java.lang.String.class);
}
