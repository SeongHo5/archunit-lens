# ArchUnit Lens

[![CI](https://github.com/archunit-lens/archunit-lens/actions/workflows/ci.yml/badge.svg)](https://github.com/archunit-lens/archunit-lens/actions/workflows/ci.yml)
[English](README.md)

**ArchUnit 규칙 위반을 코딩 중 IDE에서 먼저 확인합니다.** ArchUnit Lens는 보수적으로 정적 해석 가능한 Java ArchUnit rule subset을 IntelliJ IDEA live inspection으로 보여주는 플러그인입니다. 빠른 피드백을 제공하지만 최종 기준은 계속 ArchUnit 테스트입니다.

## 처음 5분 흐름

1. IntelliJ IDEA에서 플러그인을 설치하거나 `runIde`로 실행합니다.
2. `@ArchTest static final ArchRule` field가 있는 Java 프로젝트를 엽니다.
3. rule의 `@AnalyzeClasses(packages = ...)` scope 안에 있는 Java 파일을 수정합니다.
4. 에디터 warning과 안전한 quick fix를 확인합니다.
5. 오른쪽 tool window bar의 **ArchUnit Lens**에서 발견된 rule을 확인합니다.

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

## 0.1.0 지원 범위

ArchUnit Lens는 정적으로 증명 가능한 Java rule field 패턴만 live warning으로 표시합니다. 지원하지 않거나 의미가 불확실한 DSL chain은 가능한 경우 Rule Overview metadata로만 보존하며 live warning을 만들지 않습니다. canonical 지원 범위는 [`docs/rule-support-matrix.md`](docs/rule-support-matrix.md)입니다.

최초 live warning subset은 다음입니다.

- `resideInAPackage(...)` / `resideInAnyPackage(...)`, explicit import, resolved Java reference 기반 package dependency ban
- class suffix rule
- forbidden annotation rule
- annotation exclusivity rule
- `beAssignableTo(...)` 대상이 resolve되는 QueryMapper 형태 interface rule
- literal class/method meta-annotation rule
- `@AnalyzeClasses(packages = ...)` scope와 `.because("...")` reason 표시

## Rule Overview

오른쪽 tool window bar에서 **ArchUnit Lens**를 열면 발견된 rule을 확인할 수 있습니다. overview에는 지원/미지원 rule field filter, rule 이름 검색, source navigation, 현재 파일 기준 보기, subject/unsupported reason grouping, reason, scan metrics, package cache metrics, indexing/stale fallback 진단이 표시됩니다.

## Quick Fix

모든 warning에는 **Go to ArchUnit rule**이 붙습니다. 일부 위반에는 직접 수정 action도 붙습니다.

- class suffix 누락 -> 필요한 suffix를 붙이도록 class 이름 변경
- forbidden annotation / `notBeAnnotatedWith(...)` 위반 -> 금지된 annotation 제거

dependency 위반은 navigation/explanation 중심입니다. 플러그인은 import나 아키텍처 의존성을 자동으로 바꾸지 않습니다.

## 알려진 한계

ArchUnit Lens는 ArchUnit rule이나 사용자/프로젝트 코드를 실행하지 않습니다. 현재 다음에는 live warning을 만들지 않습니다.

- custom `ArchCondition`, `DescribedPredicate`, helper method, lambda
- method-style `@ArchTest` rule
- Kotlin ArchUnit rule parsing 또는 Kotlin target-source inspection
- resolved referenced class가 없는 unused wildcard import statement
- resolve되지 않는 dependency reference. resolve 실패는 의도적으로 warning을 만들지 않습니다.
- literal meta-annotation subset 밖의 임의 method/member subject rule
- 임의 boolean predicate/condition tree 평가

## 설정

**Settings | Tools | ArchUnit Lens**에서 rule family별 enable/disable, overview 표시, scan exclusion, 진단, metrics logging을 설정합니다. Inspection severity는 IntelliJ inspection profile을 기준으로 유지합니다.

## 호환성과 로컬 개발

- 플러그인 버전: `0.1.0`
- 로컬 IntelliJ Platform test: JDK 17 사용
- GitHub Actions CI: JDK 21 사용
- ArchUnit reference source는 `gradle.properties`의 `archUnit.reference.version`으로 고정하고 로컬 DSL 분석용으로만 풀어둡니다. ArchUnit은 플러그인 compile/runtime classpath에 올라가지 않습니다.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat runIde
```

검증 명령:

```powershell
.\gradlew.bat ktlintCheck
.\gradlew.bat test
.\gradlew.bat check
.\gradlew.bat buildPlugin
.\gradlew.bat verifyPlugin
```

기여 규칙과 fixture 작성 기준은 [`CONTRIBUTING.md`](CONTRIBUTING.md)를 참고하세요.

## 문제 확인

warning이 뜨지 않는다면 다음을 확인하세요.

1. rule이 `@ArchTest static final ArchRule` field인지 확인합니다.
2. `@AnalyzeClasses(packages = ...)`가 있다면 현재 Java 파일 package가 scope 안인지 확인합니다.
3. [support matrix](docs/rule-support-matrix.md)에서 rule 형태를 확인합니다.
4. **ArchUnit Lens** tool window에서 unsupported metadata 또는 scan metrics를 확인합니다.
5. `runIde`에서는 IDE log에서 `ArchUnit Lens scan completed`를 검색합니다.
