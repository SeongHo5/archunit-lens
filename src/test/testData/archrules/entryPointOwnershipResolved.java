package com.example;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.example.OtherRoots.*;

class EntryPointOwnershipResolved {
    @ArchTest static final ArchRule rule = classes().should().beInterfaces();
}
