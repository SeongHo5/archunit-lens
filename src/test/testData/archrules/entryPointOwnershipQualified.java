package com.example;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

class ArchitectureRules {
    @ArchTest static final ArchRule rule = ArchRuleDefinition.classes().should().beInterfaces();
}
