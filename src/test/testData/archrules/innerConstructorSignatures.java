package com.example;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
class ArchitectureRules {
    @ArchTest static final ArchRule source_signatures = noClasses().should()
            .callConstructor(org.example.Outer.ImplicitInner.class)
            .orShould().callConstructor(org.example.Outer.ExplicitInner.class)
            .orShould().callConstructor(org.example.Outer.ExplicitInner.class, java.lang.String.class);
    @ArchTest static final ArchRule jvm_signatures = noClasses().should()
            .callConstructor(org.example.Outer.ImplicitInner.class, org.example.Outer.class)
            .orShould().callConstructor(org.example.Outer.ExplicitInner.class, org.example.Outer.class)
            .orShould().callConstructor(org.example.Outer.ExplicitInner.class,
                    org.example.Outer.class, java.lang.String.class)
            .orShould().callConstructor(org.example.Outer.StaticNested.class)
            .orShould().callConstructor(org.example.Outer.StaticNested.class, java.lang.String.class)
            .orShould().callConstructor(org.example.TopLevel.class)
            .orShould().callConstructor(org.example.TopLevel.class, java.lang.String.class);
}
