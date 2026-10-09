package com.example;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import com.example.ArchRuleDefinition;
import static com.example.InheritedRoots.classes;
import static com.example.OtherRoots.noClasses;

class ArchitectureRules {
    @ArchTest static final ArchRule same_name_class = ArchRuleDefinition.classes().should().beInterfaces();
    @ArchTest static final ArchRule inherited_helper = classes().should().beInterfaces();
    @ArchTest static final ArchRule imported_helper = noClasses().that().resideInAPackage("com.example")
            .should().beAnnotatedWith("com.example.Marker");
}
