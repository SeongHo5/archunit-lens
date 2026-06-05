# ArchUnit Lens

[English](README.md)

ArchUnit Lens는 지원되는 ArchUnit rule 위반을 IntelliJ IDEA Java 에디터 inspection으로 보여주는 플러그인입니다. 코딩 중 빠른 피드백을 제공하지만, 최종 기준은 여전히 ArchUnit 테스트입니다.

## 무엇을 하나요?

플러그인은 다음과 같은 Java ArchUnit field를 정적으로 읽습니다.

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

지원되는 rule을 위반하는 Java class가 있으면 문제 위치에 warning을 표시하고, 안전한 경우 quick fix를 제공합니다.

## 지원하는 rule 형태

- package dependency ban:
  `noClasses().that().resideInAPackage(...).should().dependOnClassesThat().resideInAPackage(...)`
- class suffix rule:
  `classes().that().resideInAPackage(...).should().haveSimpleNameEndingWith(...)`
- forbidden annotation:
  `noClasses().that().resideInAPackage(...).should().beAnnotatedWith(Annotation.class)`
- annotation exclusivity:
  `classes().that().areAnnotatedWith(...).should().notBeAnnotatedWith(...)`
- `@AnalyzeClasses(packages = "...")` package scope
- `.because("...")` reason warning 표시

## Quick Fix

모든 warning에는 **Go to ArchUnit rule**이 붙습니다. 일부 위반에는 직접 수정 action도 붙습니다.

- class suffix 누락 → 필요한 suffix를 붙이도록 class 이름 변경
- forbidden annotation / `notBeAnnotatedWith(...)` 위반 → 금지된 annotation 제거

## 현재 한계

ArchUnit Lens는 ArchUnit rule이나 사용자 코드를 실행하지 않습니다. 현재는 다음을 지원하지 않습니다.

- `proxyAnnotations()` 같은 custom `ArchCondition` 또는 `DescribedPredicate` helper
- method-style `@ArchTest` rule
- Kotlin ArchUnit rule parsing
- wildcard import, inline FQN, 전체 dependency graph 분석
- `methods().that().areDeclaredInClassesThat()` 같은 method/member subject rule
- rule overview tool window

## 문제 확인

warning이 뜨지 않는다면 다음을 확인하세요.

1. rule이 `@ArchTest static final ArchRule` field인지 확인합니다.
2. `@AnalyzeClasses(packages = ...)`가 있다면 현재 파일 package가 scope 안인지 확인합니다.
3. rule 형태가 위 지원 목록에 있는지 확인합니다.
4. `runIde`에서는 IDE log에서 `ArchUnit Lens scan completed`를 검색해 rule scan 여부와 개수를 확인할 수 있습니다.

## 로컬에서 실행

JDK 17을 사용합니다.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat runIde
```

검증:

```powershell
.\gradlew.bat ktlintCheck test
```
