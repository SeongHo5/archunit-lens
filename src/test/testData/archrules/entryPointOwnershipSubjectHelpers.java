package com.example;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

class ArchitectureRules {
    private static Object classes() { return null; }
    private static Object noClasses() { return null; }
    private static Object methods() { return null; }
    private static Object noMethods() { return null; }
    private static Object noFields() { return null; }
    private static Object constructors() { return null; }

    @ArchTest static final ArchRule class_helper = classes().should().beInterfaces();
    @ArchTest static final ArchRule negative_class_helper = noClasses().that().resideInAPackage("com.example")
            .should().beAnnotatedWith("com.example.Marker");
    @ArchTest static final ArchRule method_helper = methods().should().beStatic();
    @ArchTest static final ArchRule negative_method_helper = noMethods().should().beStatic();
    @ArchTest static final ArchRule field_helper = noFields().should().beFinal();
    @ArchTest static final ArchRule constructor_helper = constructors().should().bePrivate();
}
