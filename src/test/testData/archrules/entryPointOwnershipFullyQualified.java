package com.example;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

class ArchitectureRules {
    @ArchTest static final ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes().should().beInterfaces();
}
