# ArchUnit Lens

[한국어](README.ko.md)

ArchUnit Lens is an IntelliJ IDEA plugin that shows supported ArchUnit rule violations as live Java editor inspections. It gives early feedback while you code; your ArchUnit tests remain the final authority.

## What it does

The plugin statically reads Java ArchUnit fields like:

```java
@ArchTest
static final ArchRule mapperAnnotationMustBeExclusive =
    classes()
        .that()
        .areAnnotatedWith("org.mapstruct.Mapper")
        .should()
        .notBeAnnotatedWith("com.example.SecondaryMapper")
        .because("Mapper annotations must be exclusive.");
```

When a matching Java class violates a supported rule, ArchUnit Lens highlights the offending source line and offers quick fixes where they are safe.

## Supported rule shapes

- Package dependency bans:
  `noClasses().that().resideInAPackage(...).should().dependOnClassesThat().resideInAPackage(...)`
- Class suffix rules:
  `classes().that().resideInAPackage(...).should().haveSimpleNameEndingWith(...)`
- Forbidden annotations:
  `noClasses().that().resideInAPackage(...).should().beAnnotatedWith(Annotation.class)`
- Annotation exclusivity:
  `classes().that().areAnnotatedWith(...).should().notBeAnnotatedWith(...)`
- `@AnalyzeClasses(packages = "...")` package scoping
- `.because("...")` reason text in warning details

## Quick fixes

Every warning includes **Go to ArchUnit rule**. Some violations also include a direct fix:

- Missing class suffix → rename the class by appending the required suffix.
- Forbidden annotation / `notBeAnnotatedWith(...)` violation → remove the forbidden annotation.

## Current limitations

ArchUnit Lens does not execute ArchUnit rules or user code. It intentionally does not support:

- custom `ArchCondition` or `DescribedPredicate` helpers such as `proxyAnnotations()`
- method-style `@ArchTest` rules
- Kotlin ArchUnit rule parsing
- wildcard imports, inline fully qualified references, or full dependency graph analysis
- method/member subject rules such as `methods().that().areDeclaredInClassesThat()`
- a rule overview tool window

## Troubleshooting

If a warning does not appear:

1. Check that the rule is an `@ArchTest static final ArchRule` field.
2. Check that the edited file package is inside `@AnalyzeClasses(packages = ...)`, if that annotation is present.
3. Check that the rule shape is listed above.
4. In `runIde`, open the IDE log and search for `ArchUnit Lens scan completed` to confirm rule discovery counts.

## Try it locally

Use JDK 17:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat runIde
```

Run verification:

```powershell
.\gradlew.bat ktlintCheck test
```
