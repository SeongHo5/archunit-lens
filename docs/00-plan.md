# ArchUnit Lens 기획 문서

> 기존 ArchUnit 테스트 규칙을 IntelliJ IDEA의 live inspection으로 먼저 보여주어, 아키텍처 위반을 테스트 실행 전에 발견하게 하는 IntelliJ 플러그인.

## 1. 프로젝트 개요

- **프로젝트명:** ArchUnit Lens
- **프로젝트 유형:** IntelliJ IDEA Plugin
- **핵심 메시지:** See ArchUnit violations while coding.
- **한국어 메시지:** ArchUnit 규칙 위반을 테스트 실행 전에 IDE에서 먼저 본다.

ArchUnit Lens는 ArchUnit을 대체하지 않는다. 기존 ArchUnit 테스트를 source of truth로 유지하면서, 정적으로 해석 가능한 일부 Java DSL rule pattern을 IntelliJ inspection으로 변환해 코딩 중에 빠른 피드백을 제공한다.

```text
ArchUnit tests
  ├─ JUnit / CI에서 최종 검증
  └─ ArchUnit Lens가 지원 가능한 subset을 IDE live warning으로 표시
```

목표는 “ArchUnit을 다르게 실행하는 것”이 아니라 **더 이른 시점에 ArchUnit 규칙 위반 가능성을 보여주는 것**이다.

## 2. 문제 정의

ArchUnit은 Java/Spring 프로젝트에서 계층, 패키지, 의존성, 네이밍 같은 아키텍처 규칙을 테스트로 검증할 수 있게 해준다.

예를 들어 다음 규칙은 domain 계층이 infrastructure 계층에 의존하지 못하게 한다.

```java
@ArchTest
static final ArchRule domain_should_not_depend_on_infrastructure =
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..infrastructure..");
```

하지만 일반적인 피드백 흐름은 늦다.

```text
코드 작성
→ 테스트 실행
→ ArchUnit 테스트 실패
→ 실패 메시지 확인
→ 위반 위치 탐색
→ 코드 수정
```

이 흐름에는 다음 문제가 있다.

- 코드 작성 중에는 아키텍처 위반을 즉시 알기 어렵다.
- PR 또는 CI 단계에서야 규칙 위반을 발견하는 경우가 많다.
- 실패 메시지에서 실제 위반 코드로 다시 이동해야 한다.
- 신규 팀원이 프로젝트의 아키텍처 규칙을 체감하기 어렵다.
- 코드 리뷰에서 계층 의존성 위반 지적이 반복된다.

## 3. 해결 방향

ArchUnit Lens는 기존 ArchUnit rule field를 읽고, 지원 가능한 rule pattern만 live inspection으로 표시한다.

핵심 원칙은 다음과 같다.

1. **별도 DSL 없음**
   - `archunit-lens.yml` 같은 별도 설정 DSL을 만들지 않는다.
   - 기존 ArchUnit Java DSL이 source of truth다.

2. **IDE는 조기 피드백, ArchUnit은 최종 검증**
   - IDE warning은 빠른 사전 경고다.
   - 최종 판정은 기존 ArchUnit 테스트와 CI가 담당한다.

3. **지원 가능한 subset부터 정확하게**
   - ArchUnit Java DSL 전체를 해석하지 않는다.
   - v0.1은 PSI 분석이 명확하고 action을 제안하기 쉬운 3개 rule pattern에 집중한다.

4. **False negative 우선 허용**
   - 첫 MVP에서는 놓치는 케이스가 있어도 된다.
   - 대신 표시한 warning은 fixture 기준 명확하게 맞아야 한다.

## 4. 주요 사용자

### 1차 사용자

- ArchUnit을 이미 사용하는 Java/Spring 팀
- Layered Architecture, Clean Architecture, Hexagonal Architecture를 적용하는 팀
- 멀티모듈 Spring Boot 프로젝트를 운영하는 팀
- CI에서 ArchUnit 테스트를 실행하는 팀

### 2차 사용자

- ArchUnit 도입을 고민하는 팀
- 아키텍처 규칙을 코드 리뷰로만 관리하는 팀
- 신규 팀원에게 계층 규칙을 빠르게 학습시키고 싶은 팀

## 5. v0.1 목표

v0.1은 “제품 전체”가 아니라 **작고 명확한 live inspection MVP**다.

첫 개발 사이클의 성공 기준은 다음과 같다.

- IntelliJ Plugin Template scaffold를 ArchUnit Lens 프로젝트로 정리한다.
- 기존 ArchUnit rule field를 PSI로 정적으로 파싱한다.
- Java 파일에서 3개 rule pattern 위반을 `LocalInspectionTool` 기반 warning으로 표시한다.
- 각 warning에 최소한 `Go to ArchUnit rule` action을 제공한다.
- fixture/test로 parser와 inspection 동작을 검증한다.

## 6. v0.1 지원 대상

v0.1에서 읽는 ArchUnit rule source shape는 다음 형태로 제한한다.

```java
@ArchTest
static final ArchRule ruleName = ...;
```

초기 버전에서는 다음 형태를 제외한다.

```java
@ArchTest
void ruleName(JavaClasses classes) {
    ...
}
```

## 7. v0.1 지원 rule pattern

### 7.1 Package Dependency Ban

특정 패키지의 클래스가 금지된 패키지에 의존하지 못하게 하는 규칙이다.

```java
@ArchTest
static final ArchRule domain_should_not_depend_on_infrastructure =
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..infrastructure..");
```

초기 inspection 대상은 import statement다.

```java
package com.example.domain.order;

import com.example.infrastructure.persistence.OrderJpaRepository;
```

IDE warning 예시:

```text
ArchUnit rule violation before test run:
domain_should_not_depend_on_infrastructure

Current package '..domain..' must not depend on '..infrastructure..'.
```

Action:

- Go to ArchUnit rule

v0.1에서는 다음은 후순위로 둔다.

- wildcard import
- inline fully-qualified name usage
- resolved type graph 분석
- field/method/constructor reference 전체 분석

### 7.2 Class Name Suffix Rule

특정 패키지의 클래스명이 지정된 suffix로 끝나야 하는 규칙이다.

```java
@ArchTest
static final ArchRule controller_classes_should_end_with_controller =
        classes()
                .that()
                .resideInAPackage("..controller..")
                .should()
                .haveSimpleNameEndingWith("Controller");
```

위반 코드 예시:

```java
package com.example.presentation.controller;

public class UserApi {
}
```

IDE warning 예시:

```text
Classes in '..controller..' should have simple name ending with 'Controller'.
Rule: controller_classes_should_end_with_controller
```

Action 후보:

- Go to ArchUnit rule
- Rename class guidance/action, feasible한 경우 IntelliJ rename action 연결

### 7.3 Forbidden Annotation Rule

특정 패키지의 클래스에 금지된 annotation이 붙지 못하게 하는 규칙이다.

```java
@ArchTest
static final ArchRule domain_should_not_be_service =
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .beAnnotatedWith(Service.class);
```

`Service`, `Repository` 등 annotation 종류는 feature 차이가 아니다. v0.1에서는 annotation FQN을 파라미터로 가지는 하나의 rule model로 처리한다.

```kotlin
data class ForbiddenAnnotationRule(
    val ruleName: String,
    val sourcePackagePattern: String,
    val forbiddenAnnotationQualifiedName: String,
    val sourceElement: PsiElement,
)
```

위반 코드 예시:

```java
package com.example.domain.order;

import org.springframework.stereotype.Service;

@Service
public class OrderPolicy {
}
```

IDE warning 예시:

```text
Classes in '..domain..' must not be annotated with '@Service'.
Rule: domain_should_not_be_service
```

Action 후보:

- Go to ArchUnit rule
- Remove forbidden annotation

## 8. 내부 모델 초안

v0.1의 parser는 ArchUnit Java DSL method chain을 PSI로 분석해 다음 수준의 내부 모델로 변환한다.

```kotlin
sealed interface LiveArchRule {
    val ruleName: String
    val sourceElement: PsiElement
}

data class PackageDependencyBanRule(
    override val ruleName: String,
    val sourcePackagePattern: String,
    val forbiddenPackagePatterns: List<String>,
    override val sourceElement: PsiElement,
) : LiveArchRule

data class ClassNameSuffixRule(
    override val ruleName: String,
    val sourcePackagePattern: String,
    val requiredSuffix: String,
    override val sourceElement: PsiElement,
) : LiveArchRule

data class ForbiddenAnnotationRule(
    override val ruleName: String,
    val sourcePackagePattern: String,
    val forbiddenAnnotationQualifiedName: String,
    override val sourceElement: PsiElement,
) : LiveArchRule
```

`sourceElement`는 `Go to ArchUnit rule` action의 navigation target으로 사용한다.

## 9. UX 설계

### 9.1 Editor Warning

위반 위치에 warning을 표시한다.

- package dependency ban: import statement
- class name suffix rule: class declaration/name identifier
- forbidden annotation rule: annotation element

### 9.2 Quick Fix / Intention Action

v0.1 baseline action:

- **Go to ArchUnit rule**

가능한 경우 추가 action:

- class name suffix 위반: rename guidance/action
- forbidden annotation 위반: remove annotation

### 9.3 Inspection Settings

초기 설정 구조 후보:

```text
Settings > Editor > Inspections
  Java
    Architecture
      ArchUnit Lens
        [x] Package dependency ban violations
        [x] Class name suffix violations
        [x] Forbidden annotation violations
```

### 9.4 Tool Window 제외

v0.1에서는 Tool Window를 만들지 않는다.

이유:

- inspection-first MVP에 집중한다.
- 전체 프로젝트 scan UI는 성능/상태 관리 부담이 크다.
- 현재 목표는 “작성 중 경고”의 가치 검증이다.

## 10. 정확도 전략

v0.1은 conservative inspection을 목표로 한다.

- **허용:** false negative
- **지양:** false positive
- **원칙:** 표시한 warning은 fixture 기준 명확하게 맞아야 한다.

v0.1에서 우선 제외하는 케이스:

- wildcard import
- inline fully-qualified name usage
- resolved type graph 기반 전체 참조 분석
- unused import 제외 판단
- field type, method return type, parameter type, constructor call, static method call 분석

이 케이스들은 v0.2 이후에 확장한다.

## 11. 성능 전략

v0.1은 고급 프로젝트-wide indexing/cache를 완성하는 단계가 아니다.

우선순위:

1. 정적 PSI parser 구현
2. `LocalInspectionTool` 기반 실제 warning 표시
3. fixture/test로 3개 패턴 검증
4. 최소한의 rule lookup/cache

후순위:

- 전체 프로젝트 ArchUnit rule 자동 indexing 고도화
- 증분 cache refresh
- resolved type cache
- dependency graph partial cache
- 변경 파일 기반 최적화

피해야 할 것:

- 매 inspection마다 전체 프로젝트 재스캔
- live inspection 중 ArchUnit test 실행
- custom rule runtime execution

## 12. 비목표

v0.1에서 하지 않을 것:

- ArchUnit 전체 Java DSL 완전 해석
- custom `ArchCondition` 실행
- custom `DescribedPredicate` 정적 해석
- method 형태의 `@ArchTest` rule 지원
- 모든 `layeredArchitecture()` 케이스 지원
- 모든 `slices()` 케이스 지원
- Kotlin ArchUnit 테스트 완전 지원
- 별도 DSL 제공
- CI 검증 대체
- ArchUnit 자체 대체
- Tool Window 제공
- public release / marketplace 전략 확정
- 새 외부 dependency 추가

## 13. 결정 경계

### Codex/OMX가 자율 결정해도 되는 것

- plugin id/package rename 세부안
- IntelliJ Plugin Template sample code 제거
- 내부 model/class naming
- test fixture 구성
- 3개 rule pattern 구현 순서
- parser/inspection/test 경계 설계

### 사용자 확인이 필요한 것

- 지원 IntelliJ IDEA version range 변경 또는 확정
- public release / marketplace 전략
- 새 외부 dependency 추가
- v0.1 rule pattern 확대
- “기존 ArchUnit test가 source of truth”라는 포지셔닝 변경

## 14. 테스트/검증 기준

v0.1 완료 판단 기준:

- 3개 rule pattern에 대한 ArchUnit rule fixture가 존재한다.
- parser 또는 inspection test가 각 rule shape 인식을 검증한다.
- inspection test가 각 pattern의 명확한 위반에 warning을 표시함을 검증한다.
- 기본 negative case에서 불필요한 warning이 표시되지 않음을 검증한다.
- 각 warning에서 최소 `Go to ArchUnit rule` action을 제공한다.
- 가능한 경우 class rename guidance/action, annotation remove action을 제공한다.
- template sample 기능에 의존하는 테스트를 제거하거나 product test로 대체한다.
- Gradle/IntelliJ plugin test path로 검증할 수 있다.

## 15. 구현 단계 초안

### Step 1. Template cleanup

- project name, plugin id, package rename
- sample Tool Window/startup/service 제거
- `plugin.xml`을 ArchUnit Lens 중심으로 정리
- README/문서 skeleton 정리

### Step 2. Rule source discovery 최소 구현

- Java PSI에서 `@ArchTest static final ArchRule` field 탐색
- initializer expression 확보
- rule name과 source element 보존

### Step 3. Static PSI parser

- package dependency ban parser
- class name suffix parser
- forbidden annotation parser
- unsupported rule은 조용히 무시

### Step 4. Local inspections

- import-level package dependency warning
- class declaration/name suffix warning
- annotation-level forbidden annotation warning

### Step 5. Actions

- Go to ArchUnit rule
- remove annotation action
- rename guidance/action 가능한 범위 적용

### Step 6. Fixture tests

- parser fixtures
- inspection highlighting fixtures
- negative fixtures
- action/navigation fixtures 가능한 범위

## 16. Roadmap

### v0.1

- 3개 PSI-clear rule pattern
- static parser
- LocalInspectionTool
- fixture-backed warnings/actions

### v0.2

- wildcard import / inline FQN 일부 지원
- resolved type reference 분석 시작
- unused import 고려
- rule cache refresh 고도화
- changed file 기반 inspection 최적화

### v0.3+

- field/method/constructor/reference 분석 확대
- annotation placement rule 추가 검토
- layeredArchitecture subset 검토
- unsupported rule hint 옵션
- Tool Window 또는 rule overview UI 검토

## 17. 핵심 포지셔닝

ArchUnit Lens는 “ArchUnit을 IDE에서 대체 실행하는 도구”가 아니다.

ArchUnit Lens는 **기존 ArchUnit 테스트 규칙 중 정적으로 해석 가능한 일부를 IDE에서 먼저 보여주는 조기 피드백 레이어**다.

```text
Supported ArchUnit rule patterns are inspected live.
All ArchUnit rules still run normally through ArchUnit tests.
```