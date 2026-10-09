package com.example;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.example.OtherRoots.classes;

class ArchitectureRules {
    @ArchTest static final ArchRule rule = classes().should().beInterfaces();
}
