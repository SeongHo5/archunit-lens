package io.github.archunitlens.rules

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile

internal enum class ExactHandlerFamily {
    PACKAGE_DEPENDENCY_BAN,
    CLASS_NAME_SUFFIX,
    FORBIDDEN_ANNOTATION,
    ANNOTATION_EXCLUSIVITY,
    INTERFACE_NAMING,
    CLASS_META_ANNOTATION,
    METHOD_META_ANNOTATION,
}

internal sealed interface ExactHandlerDecision {
    data class Matched(val rule: LiveArchRule) : ExactHandlerDecision
    data class Unsupported(val reason: UnsupportedReason) : ExactHandlerDecision
    data object NotApplicable : ExactHandlerDecision
}

/**
 * Converts supported Java ArchUnit DSL method chains into live inspection rules.
 *
 * Unsupported or ambiguous chains return `null` rather than partially guessing.
 */
object ArchRuleParser {
    fun discover(source: ArchRuleSource): DiscoveredArchRule? {
        val calls = RawCallExtractor.from(source.initializer)
        if (calls.isEmpty()) return null

        return RuleNormalizer.normalize(source, calls)
    }

    private object RuleNormalizer {
        fun normalize(
            source: ArchRuleSource,
            calls: List<RawCall>,
        ): DiscoveredArchRule = when (val decision = routeExactHandlers(source, calls) { parseClassConvention(source, calls) }) {
            is ExactHandlerDecision.Matched -> DiscoveredArchRule(
                ruleName = source.ruleName,
                descriptor = decision.rule.toDescriptor(calls, source),
                liveRule = decision.rule,
            )
            is ExactHandlerDecision.Unsupported -> DiscoveredArchRule(
                ruleName = source.ruleName,
                descriptor = unsupportedDescriptor(source, calls, decision.reason),
                liveRule = null,
            )
            ExactHandlerDecision.NotApplicable -> DiscoveredArchRule(
                ruleName = source.ruleName,
                descriptor = unsupportedDescriptor(source, calls),
                liveRule = null,
            )
        }
    }

    internal fun routeExactHandlers(
        source: ArchRuleSource,
        calls: List<RawCall>,
        fallback: () -> ExactHandlerDecision,
    ): ExactHandlerDecision {
        ExactHandlerFamily.entries.forEach { family ->
            when (val decision = classifyExactHandler(family, source, calls)) {
                ExactHandlerDecision.NotApplicable -> Unit
                else -> return decision
            }
        }
        return fallback()
    }

    internal fun classifyExactHandler(
        family: ExactHandlerFamily,
        source: ArchRuleSource,
        calls: List<RawCall>,
    ): ExactHandlerDecision {
        if (!family.owns(calls)) return ExactHandlerDecision.NotApplicable
        calls.validateStaticArguments()?.let { reason ->
            val metaAnnotationFamily = family == ExactHandlerFamily.CLASS_META_ANNOTATION ||
                family == ExactHandlerFamily.METHOD_META_ANNOTATION
            val stableReason = if (metaAnnotationFamily && reason !is UnsupportedReason.UnresolvedSymbol) {
                UnsupportedReason.CustomOrMetaAnnotationPredicates
            } else {
                reason
            }
            return ExactHandlerDecision.Unsupported(stableReason)
        }

        val rule = when (family) {
            ExactHandlerFamily.PACKAGE_DEPENDENCY_BAN -> parsePackageDependencyBan(source, calls)
            ExactHandlerFamily.CLASS_NAME_SUFFIX -> parseClassNameSuffix(source, calls)
            ExactHandlerFamily.FORBIDDEN_ANNOTATION -> parseForbiddenAnnotation(source, calls)
            ExactHandlerFamily.ANNOTATION_EXCLUSIVITY -> parseAnnotationExclusivity(source, calls)
            ExactHandlerFamily.INTERFACE_NAMING -> parseInterfaceNaming(source, calls)
            ExactHandlerFamily.CLASS_META_ANNOTATION -> parseClassMetaAnnotation(source, calls)
            ExactHandlerFamily.METHOD_META_ANNOTATION -> parseMethodMetaAnnotation(source, calls)
        }
        return rule?.let(ExactHandlerDecision::Matched)
            ?: ExactHandlerDecision.Unsupported(calls.unresolvedReason())
    }

    private fun parseClassConvention(
        source: ArchRuleSource,
        calls: List<RawCall>,
    ): ExactHandlerDecision {
        if (calls.firstOrNull()?.name != "classes") return ExactHandlerDecision.NotApplicable
        calls.validateStaticArguments()?.let { return ExactHandlerDecision.Unsupported(it) }
        if (calls.dropLast(1).any { it.name == "because" }) {
            return ExactHandlerDecision.Unsupported(UnsupportedReason.UnsupportedOrAmbiguousRuleChain)
        }
        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        if (shouldIndex < 1) return ExactHandlerDecision.Unsupported(UnsupportedReason.UnsupportedOrAmbiguousRuleChain)
        val predicate = calls.take(shouldIndex).classPredicate(source.initializer)
            ?: return ExactHandlerDecision.Unsupported(calls.classFallbackReason(source.initializer))
        val condition = calls.drop(shouldIndex + 1).withoutTrailingBecauseCall().classCondition(source.initializer)
            ?: return ExactHandlerDecision.Unsupported(calls.classFallbackReason(source.initializer))

        return ExactHandlerDecision.Matched(
            ClassConventionRule(
                ruleName = source.ruleName,
                predicate = predicate,
                condition = condition,
                sourcePointer = source.fieldPointer,
                analyzeScope = source.analyzeScope,
                reason = calls.reason(),
            ),
        )
    }

    private fun parsePackageDependencyBan(
        source: ArchRuleSource,
        calls: List<RawCall>,
    ): PackageDependencyBanRule? {
        if (calls.firstOrNull()?.name != "noClasses") return null

        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        val dependencyIndex = calls.indexOfFirst { it.name == "dependOnClassesThat" }
        if (shouldIndex < 0 || dependencyIndex < 0 || dependencyIndex < shouldIndex) return null
        val predicateCalls = calls.take(shouldIndex)
        val conditionCalls = calls.drop(shouldIndex + 1).withoutTrailingBecauseCall()
        if (!predicateCalls.matchesPackagePredicateShape()) return null
        if (!conditionCalls.matchesDependencyConditionShape()) return null

        val sourcePatterns = predicateCalls.packagePatternArgs()
        if (sourcePatterns.isEmpty()) return null
        val forbiddenPatterns = conditionCalls.drop(1).packagePatternArgs()
        if (forbiddenPatterns.isEmpty()) return null

        return PackageDependencyBanRule(
            ruleName = source.ruleName,
            sourcePackagePatterns = sourcePatterns,
            forbiddenPackagePatterns = forbiddenPatterns,
            sourcePointer = source.fieldPointer,
            analyzeScope = source.analyzeScope,
            reason = calls.reason(),
        )
    }

    private fun parseClassNameSuffix(
        source: ArchRuleSource,
        calls: List<RawCall>,
    ): ClassNameSuffixRule? {
        if (calls.firstOrNull()?.name != "classes") return null
        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        if (shouldIndex < 0) return null
        if (!calls.matchesExactShape(
                predicateNames = listOf("classes", "that", "resideInAPackage"),
                conditionNames = listOf("haveSimpleNameEndingWith"),
            )
        ) {
            return null
        }

        val sourcePattern = calls.take(shouldIndex)
            .firstStringArg("resideInAPackage") ?: return null
        val suffix = calls.firstStringArg("haveSimpleNameEndingWith") ?: return null

        return ClassNameSuffixRule(
            ruleName = source.ruleName,
            sourcePackagePattern = sourcePattern,
            requiredSuffix = suffix,
            sourcePointer = source.fieldPointer,
            analyzeScope = source.analyzeScope,
            reason = calls.reason(),
        )
    }

    private fun parseForbiddenAnnotation(
        source: ArchRuleSource,
        calls: List<RawCall>,
    ): ForbiddenAnnotationRule? {
        if (calls.firstOrNull()?.name != "noClasses") return null
        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        if (shouldIndex < 0) return null
        if (!calls.matchesExactShape(
                predicateNames = listOf("noClasses", "that", "resideInAPackage"),
                conditionNames = listOf("beAnnotatedWith"),
            )
        ) {
            return null
        }

        val sourcePattern = calls.take(shouldIndex)
            .firstStringArg("resideInAPackage") ?: return null
        val annotationName = calls.firstOrNull { it.name == "beAnnotatedWith" }
            ?.classLiteralArgs
            ?.firstOrNull()
            ?: return null

        return ForbiddenAnnotationRule(
            ruleName = source.ruleName,
            sourcePackagePattern = sourcePattern,
            forbiddenAnnotationQualifiedName = qualifyClassLiteral(annotationName, source.initializer) ?: return null,
            sourcePointer = source.fieldPointer,
            analyzeScope = source.analyzeScope,
            reason = calls.reason(),
        )
    }

    private fun parseAnnotationExclusivity(
        source: ArchRuleSource,
        calls: List<RawCall>,
    ): AnnotationExclusivityRule? {
        if (calls.firstOrNull()?.name != "classes") return null
        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        if (shouldIndex < 0) return null
        if (!calls.matchesExactShape(
                predicateNames = listOf("classes", "that", "areAnnotatedWith"),
                conditionNames = listOf("notBeAnnotatedWith"),
            )
        ) {
            return null
        }

        val requiredAnnotation = calls
            .take(shouldIndex)
            .firstAnnotationArg("areAnnotatedWith", source.initializer)
            ?: return null
        val forbiddenAnnotation = calls
            .drop(shouldIndex + 1)
            .firstAnnotationArg("notBeAnnotatedWith", source.initializer)
            ?: return null

        return AnnotationExclusivityRule(
            ruleName = source.ruleName,
            requiredAnnotationQualifiedName = requiredAnnotation,
            forbiddenAnnotationQualifiedName = forbiddenAnnotation,
            sourcePointer = source.fieldPointer,
            analyzeScope = source.analyzeScope,
            reason = calls.reason(),
        )
    }

    private fun parseInterfaceNaming(
        source: ArchRuleSource,
        calls: List<RawCall>,
    ): InterfaceNamingRule? {
        if (calls.firstOrNull()?.name != "classes") return null
        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        if (shouldIndex < 0) return null
        if (!calls.matchesExactShape(
                predicateNames = listOf("classes", "that", "haveSimpleNameEndingWith"),
                conditionNames = listOf("beInterfaces", "andShould", "beAssignableTo"),
            )
        ) {
            return null
        }

        val requiredSuffix = calls
            .take(shouldIndex)
            .firstStringArg("haveSimpleNameEndingWith")
            ?: return null
        val conditionCalls = calls.drop(shouldIndex + 1)
        if (conditionCalls.none { it.name == "beInterfaces" }) return null
        val assignableTo = conditionCalls
            .firstAnnotationArg("beAssignableTo", source.initializer)
            ?: return null

        return InterfaceNamingRule(
            ruleName = source.ruleName,
            requiredSuffix = requiredSuffix,
            assignableToQualifiedName = assignableTo,
            sourcePointer = source.fieldPointer,
            analyzeScope = source.analyzeScope,
            reason = calls.reason(),
        )
    }

    private fun parseClassMetaAnnotation(
        source: ArchRuleSource,
        calls: List<RawCall>,
    ): ClassMetaAnnotationRule? {
        if (calls.firstOrNull()?.name != "classes") return null
        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        if (shouldIndex < 0) return null
        if (!calls.matchesExactShape(
                predicateNames = listOf("classes", "that", "areInterfaces"),
                conditionNames = listOf("notBeMetaAnnotatedWith"),
            )
        ) {
            return null
        }
        if (calls.take(shouldIndex).none { it.name == "areInterfaces" }) return null

        val forbiddenMetaAnnotation = calls
            .drop(shouldIndex + 1)
            .firstAnnotationArg("notBeMetaAnnotatedWith", source.initializer)
            ?: return null

        return ClassMetaAnnotationRule(
            ruleName = source.ruleName,
            forbiddenMetaAnnotationQualifiedName = forbiddenMetaAnnotation,
            sourcePointer = source.fieldPointer,
            analyzeScope = source.analyzeScope,
            reason = calls.reason(),
        )
    }

    private fun parseMethodMetaAnnotation(
        source: ArchRuleSource,
        calls: List<RawCall>,
    ): MethodMetaAnnotationRule? {
        if (calls.firstOrNull()?.name != "methods") return null
        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        if (shouldIndex < 0) return null
        if (!calls.matchesExactShape(
                predicateNames = listOf("methods", "that", "areDeclaredInClassesThat", "areInterfaces"),
                conditionNames = listOf("notBeMetaAnnotatedWith"),
            )
        ) {
            return null
        }
        val predicateCalls = calls.take(shouldIndex)
        if (predicateCalls.none { it.name == "areDeclaredInClassesThat" }) return null
        if (predicateCalls.none { it.name == "areInterfaces" }) return null

        val forbiddenMetaAnnotation = calls
            .drop(shouldIndex + 1)
            .firstAnnotationArg("notBeMetaAnnotatedWith", source.initializer)
            ?: return null

        return MethodMetaAnnotationRule(
            ruleName = source.ruleName,
            forbiddenMetaAnnotationQualifiedName = forbiddenMetaAnnotation,
            sourcePointer = source.fieldPointer,
            analyzeScope = source.analyzeScope,
            reason = calls.reason(),
        )
    }

    private fun ExactHandlerFamily.owns(calls: List<RawCall>): Boolean {
        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        if (shouldIndex < 0) return false
        val predicateNames = calls.take(shouldIndex).map { it.name }
        val conditionNames = calls.drop(shouldIndex + 1).map { it.name }.withoutTrailingBecause()
        return when (this) {
            ExactHandlerFamily.PACKAGE_DEPENDENCY_BAN ->
                (
                    predicateNames in listOf(
                        listOf("noClasses", "that", "resideInAPackage"),
                        listOf("noClasses", "that", "resideInAnyPackage"),
                    ) &&
                        conditionNames in listOf(
                            listOf("dependOnClassesThat", "resideInAPackage"),
                            listOf("dependOnClassesThat", "resideInAnyPackage"),
                        )
                    )
            ExactHandlerFamily.CLASS_NAME_SUFFIX -> predicateNames == listOf("classes", "that", "resideInAPackage") &&
                conditionNames == listOf("haveSimpleNameEndingWith")
            ExactHandlerFamily.FORBIDDEN_ANNOTATION -> predicateNames == listOf("noClasses", "that", "resideInAPackage") &&
                conditionNames == listOf("beAnnotatedWith")
            ExactHandlerFamily.ANNOTATION_EXCLUSIVITY -> predicateNames == listOf("classes", "that", "areAnnotatedWith") &&
                conditionNames == listOf("notBeAnnotatedWith")
            ExactHandlerFamily.INTERFACE_NAMING -> predicateNames == listOf("classes", "that", "haveSimpleNameEndingWith") &&
                conditionNames == listOf("beInterfaces", "andShould", "beAssignableTo")
            ExactHandlerFamily.CLASS_META_ANNOTATION -> predicateNames == listOf("classes", "that", "areInterfaces") &&
                conditionNames == listOf("notBeMetaAnnotatedWith")
            ExactHandlerFamily.METHOD_META_ANNOTATION ->
                predicateNames == listOf("methods", "that", "areDeclaredInClassesThat", "areInterfaces") &&
                    conditionNames == listOf("notBeMetaAnnotatedWith")
        }
    }

    private fun List<RawCall>.validateStaticArguments(): UnsupportedReason? {
        for (call in this) {
            val expectation = when (call.name) {
                "classes", "noClasses", "methods", "that", "should", "andShould", "dependOnClassesThat",
                "and", "or", "areInterfaces", "areNotInterfaces", "areEnums", "areNotEnums",
                "beInterfaces", "notBeInterfaces", "beEnums", "notBeEnums", "areDeclaredInClassesThat",
                -> ArgumentExpectation.None
                "resideInAnyPackage" -> ArgumentExpectation.Strings(minimum = 1)
                "resideInAPackage", "haveSimpleNameEndingWith", "haveSimpleNameNotEndingWith", "because" ->
                    ArgumentExpectation.Strings(exact = 1)
                "areAnnotatedWith", "areNotAnnotatedWith", "beAnnotatedWith", "notBeAnnotatedWith",
                "notBeMetaAnnotatedWith", "beAssignableTo",
                ->
                    ArgumentExpectation.Annotation(exact = 1)
                else -> continue
            }
            expectation.validate(call)?.let { return it }
        }
        return null
    }

    private sealed interface ArgumentExpectation {
        fun validate(call: RawCall): UnsupportedReason?

        data object None : ArgumentExpectation {
            override fun validate(call: RawCall): UnsupportedReason? = if (call.arguments.isEmpty()) {
                null
            } else {
                UnsupportedReason.InvalidArity(call.name, "0", call.arguments.size)
            }
        }

        data class Strings(
            val exact: Int? = null,
            val minimum: Int? = null,
        ) : ArgumentExpectation {
            override fun validate(call: RawCall): UnsupportedReason? {
                val expected = exact?.toString() ?: "at least $minimum"
                if (exact != null && call.arguments.size != exact) {
                    return UnsupportedReason.InvalidArity(call.name, expected, call.arguments.size)
                }
                if (minimum != null && call.arguments.size < minimum) {
                    return UnsupportedReason.InvalidArity(call.name, expected, call.arguments.size)
                }
                val unsupported = call.arguments.firstOrNull { it !is RawArgument.StringLiteral }
                return unsupported?.let { UnsupportedReason.UnsupportedArgument(call.name, it.position, it.kindName()) }
            }
        }

        data class Annotation(val exact: Int) : ArgumentExpectation {
            override fun validate(call: RawCall): UnsupportedReason? {
                if (call.arguments.size != exact) {
                    return UnsupportedReason.InvalidArity(call.name, exact.toString(), call.arguments.size)
                }
                val unsupported = call.arguments.firstOrNull {
                    it !is RawArgument.StringLiteral && it !is RawArgument.ClassLiteral
                }
                unsupported?.let { return UnsupportedReason.UnsupportedArgument(call.name, it.position, it.kindName()) }
                return call.arguments
                    .filterIsInstance<RawArgument.ClassLiteral>()
                    .firstOrNull { it.resolvedQualifiedName == null }
                    ?.let { UnsupportedReason.UnresolvedSymbol(call.name, it.canonicalName) }
            }
        }
    }

    private fun RawArgument.kindName(): String = when (this) {
        is RawArgument.StringLiteral -> "string literal"
        is RawArgument.ClassLiteral -> "class literal"
        is RawArgument.Reference -> "dynamic reference"
        is RawArgument.NestedCall -> "helper call"
        is RawArgument.Lambda -> "lambda"
        is RawArgument.CustomExpression -> "custom expression"
    }

    private fun List<RawCall>.unresolvedReason(): UnsupportedReason {
        val call = firstOrNull { rawCall -> rawCall.arguments.any { it is RawArgument.ClassLiteral } }
        val symbol = call?.classLiteralArgs?.firstOrNull()
        return if (call != null && symbol != null) {
            UnsupportedReason.UnresolvedSymbol(call.name, symbol)
        } else {
            UnsupportedReason.UnsupportedOrAmbiguousRuleChain
        }
    }

    private fun List<RawCall>.classPredicate(context: PsiExpression): PredicateExpr? {
        if (firstOrNull()?.name != "classes") return null
        val remaining = drop(1)
        if (remaining.isEmpty()) return PredicateExpr.All
        if (remaining.first().name != "that") return null
        val predicateCalls = remaining.drop(1).takeIf { it.isNotEmpty() } ?: return null

        var expression: PredicateExpr? = null
        var pendingOperator: String? = null
        predicateCalls.forEach { call ->
            if (call.name == "and" || call.name == "or") {
                if (expression == null || pendingOperator != null) return null
                pendingOperator = call.name
            } else {
                if (expression != null && pendingOperator == null) return null
                val leaf = call.classPredicateLeaf(context) ?: return null
                expression = expression.appendPredicate(leaf, pendingOperator)
                pendingOperator = null
            }
        }
        return expression.takeIf { pendingOperator == null }
    }

    private fun RawCall.classPredicateLeaf(context: PsiExpression): PredicateExpr? = when (name) {
        "areAnnotatedWith" -> staticQualifiedType(context)?.let(PredicateExpr::AreAnnotatedWith)
        "areNotAnnotatedWith" -> staticQualifiedType(context)?.let(PredicateExpr::AreNotAnnotatedWith)
        "resideInAPackage", "resideInAnyPackage" -> supportedPackagePatterns()?.let(PredicateExpr::ResideInPackages)
        "haveSimpleNameEndingWith" -> stringArgs.singleOrNull()?.let(PredicateExpr::HaveSimpleNameEndingWith)
        "haveSimpleNameNotEndingWith" -> stringArgs.singleOrNull()?.let(PredicateExpr::HaveSimpleNameNotEndingWith)
        "areInterfaces" -> PredicateExpr.AreInterfaces(expected = true)
        "areNotInterfaces" -> PredicateExpr.AreInterfaces(expected = false)
        "areEnums" -> PredicateExpr.AreEnums(expected = true)
        "areNotEnums" -> PredicateExpr.AreEnums(expected = false)
        else -> null
    }

    private fun List<RawCall>.classCondition(context: PsiExpression): ConditionExpr? {
        if (isEmpty()) return null
        var expression: ConditionExpr? = null
        var expectsCondition = true
        forEach { call ->
            if (call.name == "andShould") {
                if (expectsCondition || expression == null) return null
                expectsCondition = true
            } else {
                if (!expectsCondition) return null
                val leaf = call.classConditionLeaf(context) ?: return null
                expression = expression?.let { ConditionExpr.And(it, leaf) } ?: leaf
                expectsCondition = false
            }
        }
        return expression.takeIf { !expectsCondition }
    }

    private fun RawCall.classConditionLeaf(context: PsiExpression): ConditionExpr? = when (name) {
        "beAnnotatedWith" -> staticQualifiedType(context)?.let { ConditionExpr.BeAnnotatedWith(it, required = true) }
        "notBeAnnotatedWith" -> staticQualifiedType(context)?.let { ConditionExpr.BeAnnotatedWith(it, required = false) }
        "resideInAPackage", "resideInAnyPackage" -> supportedPackagePatterns()?.let(ConditionExpr::ResideInPackages)
        "haveSimpleNameEndingWith" -> stringArgs.singleOrNull()?.let {
            ConditionExpr.HaveSimpleNameEndingWith(it, required = true)
        }
        "haveSimpleNameNotEndingWith" -> stringArgs.singleOrNull()?.let {
            ConditionExpr.HaveSimpleNameEndingWith(it, required = false)
        }
        "beInterfaces" -> ConditionExpr.BeInterfaces(required = true)
        "notBeInterfaces" -> ConditionExpr.BeInterfaces(required = false)
        "beEnums" -> ConditionExpr.BeEnums(required = true)
        "notBeEnums" -> ConditionExpr.BeEnums(required = false)
        "beAssignableTo" -> staticallyResolvableType(context)?.let(ConditionExpr::BeAssignableTo)
        else -> null
    }

    private fun RawCall.staticQualifiedType(context: PsiExpression): String? = stringArgs.singleOrNull()?.takeIf { it.contains('.') }
        ?: arguments.singleOrNull()
            ?.let { it as? RawArgument.ClassLiteral }
            ?.resolvedQualifiedName
            ?.let { qualifyClassLiteral(it, context) }

    private fun RawCall.supportedPackagePatterns(): List<String>? = stringArgs.takeIf { patterns ->
        patterns.isNotEmpty() && patterns.all(PackagePattern::isSupported)
    }

    private fun RawCall.staticallyResolvableType(context: PsiExpression): String? {
        val qualifiedName = staticQualifiedType(context) ?: return null
        return JavaPsiFacade.getInstance(context.project)
            .findClass(qualifiedName, context.resolveScope)
            ?.qualifiedName
    }

    private fun List<RawCall>.classFallbackReason(context: PsiExpression): UnsupportedReason {
        val typeCall = firstOrNull {
            it.name in setOf(
                "areAnnotatedWith",
                "areNotAnnotatedWith",
                "beAnnotatedWith",
                "notBeAnnotatedWith",
                "beAssignableTo",
            ) &&
                if (it.name == "beAssignableTo") {
                    it.staticallyResolvableType(context) == null
                } else {
                    it.staticQualifiedType(context) == null
                }
        }
        if (typeCall != null) {
            val symbol = typeCall.arguments.singleOrNull()?.let { argument ->
                when (argument) {
                    is RawArgument.StringLiteral -> argument.value
                    is RawArgument.ClassLiteral -> argument.canonicalName
                    is RawArgument.Reference -> argument.text
                    is RawArgument.NestedCall -> argument.methodName ?: "helper"
                    is RawArgument.Lambda -> "lambda"
                    is RawArgument.CustomExpression -> argument.text
                }
            }.orEmpty()
            return UnsupportedReason.UnresolvedSymbol(typeCall.name, symbol)
        }
        return UnsupportedReason.UnsupportedOrAmbiguousRuleChain
    }

    private fun unsupportedDescriptor(
        source: ArchRuleSource,
        calls: List<RawCall>,
        reason: UnsupportedReason = calls.unsupportedReason(),
    ): RuleDescriptor = RuleDescriptor(
        subject = calls.subjectKind(),
        sourcePointer = source.fieldPointer,
        scope = source.analyzeScope,
        predicate = calls.predicateExpr(source.initializer)
            ?: PredicateExpr.Leaf(calls.takeUntilShould().joinToString(".") { it.name }.ifBlank { "unknown" }),
        condition = ConditionExpr.Leaf(calls.dropAfterShould().joinToString(".") { it.name }.ifBlank { "unknown" }),
        reason = calls.reason(),
        supportStatus = SupportStatus.Unsupported(reason),
    )

    private fun LiveArchRule.toDescriptor(
        calls: List<RawCall>,
        source: ArchRuleSource,
    ): RuleDescriptor = when (this) {
        is ClassConventionRule -> RuleDescriptor(
            subject = SubjectKind.Classes,
            sourcePointer = sourcePointer,
            scope = analyzeScope,
            predicate = predicate,
            condition = condition,
            reason = reason,
            supportStatus = SupportStatus.Supported,
        )
        is PackageDependencyBanRule -> RuleDescriptor(
            subject = SubjectKind.Classes,
            sourcePointer = sourcePointer,
            scope = analyzeScope,
            predicate = calls.predicateExpr(source.initializer)
                ?: PredicateExpr.Leaf("resideInPackages(${sourcePackagePatterns.joinToString()})"),
            condition = ConditionExpr.Leaf("dependOnClassesThat.resideInPackages(${forbiddenPackagePatterns.joinToString()})"),
            reason = reason,
            supportStatus = SupportStatus.Supported,
        )
        is ClassNameSuffixRule -> RuleDescriptor(
            subject = SubjectKind.Classes,
            sourcePointer = sourcePointer,
            scope = analyzeScope,
            predicate = calls.predicateExpr(source.initializer) ?: PredicateExpr.Leaf("resideInAPackage($sourcePackagePattern)"),
            condition = ConditionExpr.Leaf("haveSimpleNameEndingWith($requiredSuffix)"),
            reason = reason,
            supportStatus = SupportStatus.Supported,
        )
        is ForbiddenAnnotationRule -> RuleDescriptor(
            subject = SubjectKind.Classes,
            sourcePointer = sourcePointer,
            scope = analyzeScope,
            predicate = calls.predicateExpr(source.initializer) ?: PredicateExpr.Leaf("resideInAPackage($sourcePackagePattern)"),
            condition = ConditionExpr.Leaf("beAnnotatedWith($forbiddenAnnotationQualifiedName)"),
            reason = reason,
            supportStatus = SupportStatus.Supported,
        )
        is AnnotationExclusivityRule -> RuleDescriptor(
            subject = SubjectKind.Classes,
            sourcePointer = sourcePointer,
            scope = analyzeScope,
            predicate = calls.predicateExpr(source.initializer) ?: PredicateExpr.Leaf("areAnnotatedWith($requiredAnnotationQualifiedName)"),
            condition = ConditionExpr.Leaf("notBeAnnotatedWith($forbiddenAnnotationQualifiedName)"),
            reason = reason,
            supportStatus = SupportStatus.Supported,
        )
        is InterfaceNamingRule -> RuleDescriptor(
            subject = SubjectKind.Classes,
            sourcePointer = sourcePointer,
            scope = analyzeScope,
            predicate = calls.predicateExpr(source.initializer) ?: PredicateExpr.Leaf("haveSimpleNameEndingWith($requiredSuffix)"),
            condition = ConditionExpr.And(
                ConditionExpr.Leaf("beInterfaces"),
                ConditionExpr.Leaf("beAssignableTo($assignableToQualifiedName)"),
            ),
            reason = reason,
            supportStatus = SupportStatus.Supported,
        )
        is ClassMetaAnnotationRule -> RuleDescriptor(
            subject = SubjectKind.Classes,
            sourcePointer = sourcePointer,
            scope = analyzeScope,
            predicate = calls.predicateExpr(source.initializer) ?: PredicateExpr.Leaf("areInterfaces"),
            condition = ConditionExpr.Leaf("notBeMetaAnnotatedWith($forbiddenMetaAnnotationQualifiedName)"),
            reason = reason,
            supportStatus = SupportStatus.Supported,
        )
        is MethodMetaAnnotationRule -> RuleDescriptor(
            subject = SubjectKind.Methods,
            sourcePointer = sourcePointer,
            scope = analyzeScope,
            predicate = PredicateExpr.Leaf("areDeclaredInClassesThat.areInterfaces"),
            condition = ConditionExpr.Leaf("notBeMetaAnnotatedWith($forbiddenMetaAnnotationQualifiedName)"),
            reason = reason,
            supportStatus = SupportStatus.Supported,
        )
    }

    private fun List<RawCall>.subjectKind(): SubjectKind = when (firstOrNull()?.name) {
        "classes", "noClasses" -> SubjectKind.Classes
        "theClass" -> SubjectKind.SingleClass
        "members" -> SubjectKind.Members
        "fields" -> SubjectKind.Fields
        "codeUnits" -> SubjectKind.CodeUnits
        "constructors" -> SubjectKind.Constructors
        "methods" -> SubjectKind.Methods
        else -> SubjectKind.CustomTransformer(firstOrNull()?.name)
    }

    private fun List<RawCall>.takeUntilShould(): List<RawCall> {
        val shouldIndex = indexOfFirst { it.name == "should" }
        return if (shouldIndex < 0) this else take(shouldIndex)
    }

    private fun List<RawCall>.dropAfterShould(): List<RawCall> {
        val shouldIndex = indexOfFirst { it.name == "should" }
        return if (shouldIndex < 0) emptyList() else drop(shouldIndex + 1)
    }

    private fun List<RawCall>.unsupportedReason(): UnsupportedReason = when {
        any { it.name == "notBeMetaAnnotatedWith" } -> UnsupportedReason.CustomOrMetaAnnotationPredicates
        firstOrNull()?.name !in setOf("classes", "noClasses", "theClass", "members", "fields", "codeUnits", "constructors", "methods") ->
            UnsupportedReason.UnsupportedEntryPoint(firstOrNull()?.name ?: "unknown")
        any { it.name == "resideInAnyPackage" } -> UnsupportedReason.UnsupportedMultiPackageRuleShape
        else -> UnsupportedReason.UnsupportedOrAmbiguousRuleChain
    }

    private fun List<RawCall>.firstStringArg(methodName: String): String? = firstOrNull { it.name == methodName }?.stringArgs?.firstOrNull()

    private fun List<RawCall>.reason(): String? = firstStringArg("because")

    private fun List<RawCall>.matchesExactShape(
        predicateNames: List<String>,
        conditionNames: List<String>,
    ): Boolean {
        val shouldIndex = indexOfFirst { it.name == "should" }
        if (shouldIndex < 0) return false
        val actualPredicateNames = take(shouldIndex).map { it.name }
        val actualConditionNames = drop(shouldIndex + 1).map { it.name }.withoutTrailingBecause()
        return actualPredicateNames == predicateNames && actualConditionNames == conditionNames
    }

    private fun List<String>.withoutTrailingBecause(): List<String> = if (lastOrNull() == "because") {
        dropLast(1)
    } else {
        this
    }

    private fun List<RawCall>.withoutTrailingBecauseCall(): List<RawCall> = if (lastOrNull()?.name == "because") {
        dropLast(1)
    } else {
        this
    }

    private fun List<RawCall>.matchesPackagePredicateShape(): Boolean {
        val names = map { it.name }
        return names == listOf("noClasses", "that", "resideInAPackage") ||
            names == listOf("noClasses", "that", "resideInAnyPackage")
    }

    private fun List<RawCall>.matchesDependencyConditionShape(): Boolean {
        val names = map { it.name }
        return names == listOf("dependOnClassesThat", "resideInAPackage") ||
            names == listOf("dependOnClassesThat", "resideInAnyPackage")
    }

    private fun List<RawCall>.packagePatternArgs(): List<String> = flatMap { call ->
        when (call.name) {
            "resideInAPackage", "resideInAnyPackage" -> call.stringArgs
            else -> emptyList()
        }
    }

    private fun List<RawCall>.firstAnnotationArg(
        methodName: String,
        context: PsiExpression,
    ): String? {
        val call = firstOrNull { it.name == methodName } ?: return null
        return call.stringArgs.firstOrNull()
            ?: call.classLiteralArgs.firstOrNull()?.let { qualifyClassLiteral(it, context) }
    }

    private fun List<RawCall>.predicateExpr(context: PsiExpression): PredicateExpr? {
        val predicateCalls = takeUntilShould()
            .drop(1)
            .filterNot { it.name == "that" }
        if (predicateCalls.isEmpty()) return PredicateExpr.All

        var expression: PredicateExpr? = null
        var pendingOperator: String? = null
        var index = 0
        while (index < predicateCalls.size) {
            val call = predicateCalls[index]
            when (call.name) {
                "and", "or" -> {
                    pendingOperator = call.name
                }
                "areDeclaredInClassesThat" -> {
                    val next = predicateCalls.getOrNull(index + 1)
                    if (next?.name != "areInterfaces") return null
                    expression = expression.appendPredicate(
                        PredicateExpr.Leaf("areDeclaredInClassesThat.areInterfaces"),
                        pendingOperator,
                    )
                    pendingOperator = null
                    index += 1
                }
                else -> {
                    val leaf = call.predicateLeaf(context) ?: return null
                    expression = expression.appendPredicate(leaf, pendingOperator)
                    pendingOperator = null
                }
            }
            index += 1
        }
        return expression ?: PredicateExpr.All
    }

    private fun PredicateExpr?.appendPredicate(
        next: PredicateExpr,
        operator: String?,
    ): PredicateExpr = when {
        this == null -> next
        operator == "or" -> PredicateExpr.Or(this, next)
        else -> PredicateExpr.And(this, next)
    }

    private fun RawCall.predicateLeaf(context: PsiExpression): PredicateExpr? = when (name) {
        "resideInAPackage" -> stringArgs.firstOrNull()?.let { PredicateExpr.Leaf("resideInAPackage($it)") }
        "resideInAnyPackage" -> stringArgs.takeIf { it.isNotEmpty() }?.let { PredicateExpr.Leaf("resideInAnyPackage(${it.joinToString()})") }
        "haveSimpleNameEndingWith" -> stringArgs.firstOrNull()?.let { PredicateExpr.Leaf("haveSimpleNameEndingWith($it)") }
        "areInterfaces" -> PredicateExpr.Leaf("areInterfaces")
        "areAnnotatedWith" -> annotationArg(context)?.let { PredicateExpr.Leaf("areAnnotatedWith($it)") }
        else -> null
    }

    private fun RawCall.annotationArg(context: PsiExpression): String? = stringArgs.firstOrNull()
        ?: classLiteralArgs.firstOrNull()?.let { qualifyClassLiteral(it, context) }

    private fun qualifyClassLiteral(name: String, context: PsiExpression): String? {
        if (name.contains('.')) return name
        val javaFile = context.containingFile as? PsiJavaFile ?: return null
        return javaFile.importList
            ?.allImportStatements
            ?.filterIsInstance<PsiImportStatement>()
            ?.firstOrNull { it.qualifiedName?.endsWith(".$name") == true }
            ?.qualifiedName
    }
}
