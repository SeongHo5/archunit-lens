import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class ArchitectureRules {
    @ArchTest static final ArchRule case1 = classes().that().resideInAPackage("..case1..").and().haveSimpleNameNotEndingWith("Never").should().beAnnotatedWith("com.example.Required");
    @ArchTest static final ArchRule case2 = classes().that().resideInAPackage("..case2..").and().haveSimpleNameNotEndingWith("Never").should().notBeAnnotatedWith("com.example.Forbidden");
    @ArchTest static final ArchRule case3 = classes().that().resideInAPackage("..case3..").and().haveSimpleNameNotEndingWith("Never").should().resideInAPackage("..required..");
    @ArchTest static final ArchRule case4 = classes().that().resideInAPackage("..case4..").and().haveSimpleNameNotEndingWith("Never").should().resideInAnyPackage("..required..", "..api..");
    @ArchTest static final ArchRule case5 = classes().that().resideInAPackage("..case5..").and().haveSimpleNameNotEndingWith("Never").should().haveSimpleNameEndingWith("Service");
    @ArchTest static final ArchRule case6 = classes().that().resideInAPackage("..case6..").and().haveSimpleNameNotEndingWith("Never").should().haveSimpleNameNotEndingWith("Impl");
    @ArchTest static final ArchRule case7 = classes().that().resideInAPackage("..case7..").and().haveSimpleNameNotEndingWith("Never").should().beInterfaces();
    @ArchTest static final ArchRule case8 = classes().that().resideInAPackage("..case8..").and().haveSimpleNameNotEndingWith("Never").should().notBeInterfaces();
    @ArchTest static final ArchRule case9 = classes().that().resideInAPackage("..case9..").and().haveSimpleNameNotEndingWith("Never").should().beEnums();
    @ArchTest static final ArchRule case10 = classes().that().resideInAPackage("..case10..").and().haveSimpleNameNotEndingWith("Never").should().notBeEnums();
    @ArchTest static final ArchRule case11 = classes().that().resideInAPackage("..case11..").and().haveSimpleNameNotEndingWith("Never").should().beAssignableTo(com.example.Base.class);
}
