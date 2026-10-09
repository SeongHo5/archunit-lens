package com.example;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.example.RealRoots.classes;

class ArchitectureRules {
    @ArchTest static final ArchRule rule = classes().should().beInterfaces();
}
