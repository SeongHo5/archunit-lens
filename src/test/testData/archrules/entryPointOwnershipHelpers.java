package com.example;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.lang.syntax.elements.GivenClasses;

class EntryPointOwnershipHelpers {
    private static GivenClasses classes() {
        return ArchRuleDefinition.noClasses();
    }

    @ArchTest static final ArchRule same_class_helper = classes().should().beInterfaces();
    @ArchTest static final ArchRule external_helper = OtherRoots.classes().should().beInterfaces();
    @ArchTest static final ArchRule partial_helper = classes().should().beInterfaces().andShould().beEnums();
    @ArchTest static final ArchRule exact_helper = OtherRoots.classes().that().resideInAPackage("com.example")
            .should().haveSimpleNameEndingWith("Api");
}

class OtherRoots {
    static GivenClasses classes() {
        return ArchRuleDefinition.noClasses();
    }
}
