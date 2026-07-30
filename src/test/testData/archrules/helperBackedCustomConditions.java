package com.example.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@AnalyzeClasses(packages = "com.example")
class ArchitectureRules {
    @ArchTest
    static final ArchRule class_helper_condition =
            classes().that().resideInAPackage("..domain..")
                    .should(customClassCondition())
                    .because("class invariant");

    @ArchTest
    static final ArchRule method_helper_condition =
            methods().that().areDeclaredInClassesThat().resideInAPackage("..mapper..")
                    .should(customMethodCondition())
                    .because("method invariant");

    @ArchTest
    static final ArchRule constructor_helper_condition =
            constructors().that().areDeclaredInClassesThat().resideInAPackage("..application..")
                    .should(customConstructorCondition())
                    .because("constructor invariant");

    @ArchTest
    static final ArchRule field_helper_condition =
            fields().that().areDeclaredInClassesThat().resideInAPackage("..service..")
                    .should(customFieldCondition())
                    .because("field invariant");

    private static ArchCondition customClassCondition() {
        return null;
    }

    private static ArchCondition customMethodCondition() {
        return null;
    }

    private static ArchCondition customConstructorCondition() {
        return null;
    }

    private static ArchCondition customFieldCondition() {
        return null;
    }
}
