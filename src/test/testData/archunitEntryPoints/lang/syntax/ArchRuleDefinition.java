package com.tngtech.archunit.lang.syntax;

import com.tngtech.archunit.lang.syntax.elements.GivenClasses;

// Root names and arities mirror ArchUnit 1.4.1; the fluent DSL is intentionally incomplete.
public class ArchRuleDefinition {
    public static GivenClasses classes() { return null; }
    public static GivenClasses noClasses() { return null; }
    public static Object members() { return null; }
    public static Object fields() { return null; }
    public static Object noFields() { return null; }
    public static Object codeUnits() { return null; }
    public static Object constructors() { return null; }
    public static Object methods() { return null; }
    public static Object noMethods() { return null; }
    public static Object theClass(String name) { return null; }
}
