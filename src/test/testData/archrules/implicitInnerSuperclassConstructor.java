package com.example;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
class ArchitectureRules {
    @ArchTest static final ArchRule inner_source_constructor = noClasses().should()
            .callConstructor(org.example.Outer.InnerParent.class);
    @ArchTest static final ArchRule inner_default_constructor = noClasses().should()
            .callConstructor(org.example.Outer.InnerParent.class, org.example.Outer.class);
}
