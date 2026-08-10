package io.github.archunitlens.rules

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile

private val ARCHUNIT_SUBJECT_ENTRY_POINTS = setOf(
    "classes",
    "noClasses",
    "theClass",
    "members",
    "fields",
    "noFields",
    "codeUnits",
    "constructors",
    "methods",
    "noMethods",
)

internal enum class ExactHandlerFamily {
    PACKAGE_DEPENDENCY_BAN,
    CLASS_NAME_SUFFIX,
    FORBIDDEN_ANNOTATION,
    ANNOTATION_EXCLUSIVITY,
    INTERFACE_NAMING,
    CLASS_META_ANNOTATION,
    METHOD_META_ANNOTATION,
    CODE_ACCESS,
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
        val callsWithSource = RawCallExtractor.callsWithSource(source.initializer)
        val calls = callsWithSource.map { it.first }
        if (calls.isEmpty()) return null

        return RuleNormalizer.normalize(source, calls, callsWithSource)
    }

    private object RuleNormalizer {
        fun normalize(
            source: ArchRuleSource,
            calls: List<RawCall>,
            callsWithSource: List<Pair<RawCall, com.intellij.psi.PsiMethodCallExpression>>,
        ): DiscoveredArchRule {
            calls.helperBackedCustomCondition()?.let { helper ->
                return DiscoveredArchRule(
                    ruleName = source.ruleName,
                    descriptor = unsupportedDescriptor(
                        source = source,
                        calls = calls,
                        reason = UnsupportedReason.HelperBackedCustomCondition,
                        condition = ConditionExpr.Leaf(helper.conditionMarker()),
                    ),
                    liveRule = null,
                )
            }

            return when (
                val decision = routeExactHandlers(source, calls) {
                    parseConvention(source, calls, callsWithSource)
                }
            ) {
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
    }

    private fun List<RawCall>.helperBackedCustomCondition(): RawArgument.NestedCall? {
        if (firstOrNull()?.name !in ARCHUNIT_SUBJECT_ENTRY_POINTS) return null
        val shouldIndex = indexOfFirst { it.name == "should" }
        if (shouldIndex < 0 || count { it.name == "should" } != 1) return null
        if (drop(shouldIndex + 1).withoutTrailingBecauseCall().isNotEmpty()) return null
        return get(shouldIndex).arguments.singleOrNull() as? RawArgument.NestedCall
    }

    private fun RawArgument.NestedCall.conditionMarker(): String = "${methodName ?: "helper"}()"

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
            ExactHandlerFamily.CODE_ACCESS -> parseNoClassesCodeAccess(source, calls)
        }
        return rule?.let(ExactHandlerDecision::Matched)
            ?: ExactHandlerDecision.Unsupported(calls.unresolvedReason())
    }

    private fun parseConvention(
        source: ArchRuleSource,
        calls: List<RawCall>,
        callsWithSource: List<Pair<RawCall, com.intellij.psi.PsiMethodCallExpression>>,
    ): ExactHandlerDecision = when (calls.firstOrNull()?.name) {
        "classes" -> parseClassConvention(source, calls, callsWithSource)
        "noFields", "methods", "noMethods", "constructors" -> parseMemberConvention(source, calls)
        else -> ExactHandlerDecision.NotApplicable
    }

    private fun parseClassConvention(
        source: ArchRuleSource,
        calls: List<RawCall>,
        callsWithSource: List<Pair<RawCall, com.intellij.psi.PsiMethodCallExpression>>,
    ): ExactHandlerDecision {
        if (calls.firstOrNull()?.name != "classes") return ExactHandlerDecision.NotApplicable
        val factResolver = StaticClassFactResolver(source, callsWithSource)
        calls.validateStaticClassArguments(factResolver)?.let { return ExactHandlerDecision.Unsupported(it) }
        if (calls.dropLast(1).any { it.name == "because" }) {
            return ExactHandlerDecision.Unsupported(UnsupportedReason.UnsupportedOrAmbiguousRuleChain)
        }
        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        if (shouldIndex < 1) return ExactHandlerDecision.Unsupported(UnsupportedReason.UnsupportedOrAmbiguousRuleChain)
        val predicate = calls.take(shouldIndex).withIndexes().classPredicate(source.initializer, factResolver)
            ?: return ExactHandlerDecision.Unsupported(calls.classFallbackReason(source.initializer))
        val condition = calls.drop(shouldIndex + 1).withoutTrailingBecauseCall()
            .withIndexes(shouldIndex + 1)
            .classCondition(source.initializer, factResolver)
            ?: return ExactHandlerDecision.Unsupported(calls.classFallbackReason(source.initializer))

        return ExactHandlerDecision.Matched(
            ClassConventionRule(
                ruleName = source.ruleName,
                predicate = predicate,
                condition = condition,
                sourcePointer = source.fieldPointer,
                analyzeScope = source.analyzeScope,
                reason = calls.reason(),
                suppressDuringDumbMode = calls.requiresDumbModeSuppression(),
            ),
        )
    }

    private fun parseMemberConvention(
        source: ArchRuleSource,
        calls: List<RawCall>,
    ): ExactHandlerDecision {
        calls.validateStaticArguments()?.let { return ExactHandlerDecision.Unsupported(it) }
        if (calls.dropLast(1).any { it.name == "because" }) {
            return ExactHandlerDecision.Unsupported(UnsupportedReason.UnsupportedOrAmbiguousRuleChain)
        }
        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        if (shouldIndex < 1 || calls.count { it.name == "should" } != 1) {
            return ExactHandlerDecision.Unsupported(UnsupportedReason.UnsupportedOrAmbiguousRuleChain)
        }
        val subject = when (calls.first().name) {
            "noFields" -> MemberSubjectKind.Fields
            "methods", "noMethods" -> MemberSubjectKind.Methods
            "constructors" -> MemberSubjectKind.Constructors
            else -> return ExactHandlerDecision.NotApplicable
        }
        val polarity = when (calls.first().name) {
            "noFields", "noMethods" -> RulePolarity.NEGATIVE
            else -> RulePolarity.POSITIVE
        }
        val predicate = calls.take(shouldIndex).memberPredicate(source.initializer, subject)
            ?: return ExactHandlerDecision.Unsupported(calls.memberFallbackReason(source.initializer))
        val condition = calls.drop(shouldIndex + 1).withoutTrailingBecauseCall().memberCondition(
            source.initializer,
            subject,
            polarity,
        )
            ?: return ExactHandlerDecision.Unsupported(calls.memberFallbackReason(source.initializer))
        return ExactHandlerDecision.Matched(
            MemberConventionRule(
                ruleName = source.ruleName,
                subject = subject,
                predicate = predicate,
                condition = condition,
                polarity = polarity,
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

    private fun parseNoClassesCodeAccess(
        source: ArchRuleSource,
        calls: List<RawCall>,
    ): NoClassesCodeAccessRule? {
        if (calls.firstOrNull()?.name != "noClasses") return null
        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        if (shouldIndex != 1 || calls.count { it.name == "should" } != 1) return null

        val conditionCalls = calls.drop(shouldIndex + 1).withoutTrailingBecauseCall()
        if (conditionCalls.isEmpty()) return null
        var condition: ConditionExpr? = null
        var expectsLeaf = true
        conditionCalls.forEach { call ->
            if (expectsLeaf) {
                val leaf = call.exactCodeAccessLeaf() ?: return null
                condition = condition?.let { ConditionExpr.Or(it, leaf) } ?: leaf
                expectsLeaf = false
            } else {
                if (call.name != "orShould") return null
                expectsLeaf = true
            }
        }
        if (expectsLeaf) return null

        return NoClassesCodeAccessRule(
            ruleName = source.ruleName,
            condition = condition ?: return null,
            sourcePointer = source.fieldPointer,
            analyzeScope = source.analyzeScope,
            reason = calls.reason(),
        )
    }

    private fun RawCall.exactCodeAccessLeaf(): ConditionExpr? {
        val owner = (arguments.getOrNull(0) as? RawArgument.ClassLiteral)?.resolvedQualifiedName ?: return null
        val memberName = (arguments.getOrNull(1) as? RawArgument.StringLiteral)?.value ?: return null
        return when (name) {
            "accessField" -> ConditionExpr.AccessField(owner, memberName)
            "callMethod" -> ConditionExpr.CallMethod(owner, memberName, emptyList())
            else -> null
        }
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
            ExactHandlerFamily.CODE_ACCESS ->
                predicateNames.firstOrNull() in setOf("classes", "noClasses") &&
                    conditionNames.any { it == "accessField" || it == "callMethod" }
        }
    }

    private fun List<RawCall>.validateStaticArguments(): UnsupportedReason? {
        for (call in this) {
            val expectation = when (call.name) {
                "classes", "noClasses", "fields", "noFields", "methods", "noMethods", "constructors",
                "that", "should", "andShould", "orShould", "dependOnClassesThat",
                "and", "or", "areInterfaces", "areNotInterfaces", "areEnums", "areNotEnums",
                "areRecords", "areNotRecords", "beInterfaces", "notBeInterfaces", "beEnums", "notBeEnums",
                "beRecords", "notBeRecords", "areDeclaredInClassesThat",
                "bePrivate", "notBePrivate", "bePublic", "notBePublic", "beProtected", "notBeProtected",
                "bePackagePrivate", "notBePackagePrivate", "beStatic", "notBeStatic", "beFinal", "notBeFinal",
                -> ArgumentExpectation.None
                "resideInAnyPackage" -> ArgumentExpectation.Strings(minimum = 1)
                "resideInAPackage", "haveSimpleNameEndingWith", "haveSimpleNameNotEndingWith", "because",
                "haveName", "notHaveName", "haveNameMatching", "notHaveNameMatching",
                ->
                    ArgumentExpectation.Strings(exact = 1)
                "areAnnotatedWith", "areNotAnnotatedWith", "beAnnotatedWith", "notBeAnnotatedWith",
                "areMetaAnnotatedWith", "areNotMetaAnnotatedWith", "beMetaAnnotatedWith", "notBeMetaAnnotatedWith",
                "beAssignableTo",
                "areAssignableTo", "areNotAssignableTo", "implement", "doNotImplement", "haveRawReturnType",
                ->
                    ArgumentExpectation.Annotation
                "accessField", "callMethod" -> ArgumentExpectation.ExactOwnerAndName
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

        data object Annotation : ArgumentExpectation {
            override fun validate(call: RawCall): UnsupportedReason? {
                if (call.arguments.size != 1) {
                    return UnsupportedReason.InvalidArity(call.name, "1", call.arguments.size)
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

        data object ExactOwnerAndName : ArgumentExpectation {
            override fun validate(call: RawCall): UnsupportedReason? {
                if (call.arguments.size != 2) {
                    return UnsupportedReason.InvalidArity(call.name, "2", call.arguments.size)
                }
                val owner = call.arguments[0]
                if (owner !is RawArgument.ClassLiteral) {
                    return UnsupportedReason.UnsupportedArgument(call.name, owner.position, owner.kindName())
                }
                if (owner.resolvedQualifiedName == null) {
                    return UnsupportedReason.UnresolvedSymbol(call.name, owner.canonicalName)
                }
                val name = call.arguments[1]
                if (name !is RawArgument.StringLiteral) {
                    return UnsupportedReason.UnsupportedArgument(call.name, name.position, name.kindName())
                }
                return null
            }
        }
    }

    private fun List<RawCall>.validateStaticClassArguments(
        factResolver: StaticClassFactResolver,
    ): UnsupportedReason? {
        forEachIndexed { index, call ->
            val reason = when (call.name) {
                "resideInAnyPackage" -> {
                    if (call.arguments.all { it is RawArgument.StringLiteral }) {
                        ArgumentExpectation.Strings(minimum = 1).validate(call)
                    } else {
                        when (val resolved = factResolver.packagePatterns(index)) {
                            is StaticArgumentResult.Resolved ->
                                resolved.value
                                    .firstOrNull { !PackagePattern.isSupported(it) }
                                    ?.let { unsupportedPattern ->
                                        UnsupportedReason.UnsupportedArgument(
                                            call.name,
                                            0,
                                            "unsupported package pattern '$unsupportedPattern'",
                                        )
                                    }
                            is StaticArgumentResult.Unresolved -> UnsupportedReason.UnresolvedSymbol(call.name, resolved.symbol)
                            is StaticArgumentResult.Unsupported -> UnsupportedReason.UnsupportedArgument(
                                call.name,
                                0,
                                resolved.detail,
                            )
                        }
                    }
                }
                "haveModifier", "notHaveModifier" -> {
                    if (call.arguments.size != 1) {
                        UnsupportedReason.InvalidArity(call.name, "1", call.arguments.size)
                    } else {
                        when (val resolved = factResolver.modifier(index)) {
                            is StaticArgumentResult.Resolved -> null
                            is StaticArgumentResult.Unresolved -> UnsupportedReason.UnresolvedSymbol(call.name, resolved.symbol)
                            is StaticArgumentResult.Unsupported -> UnsupportedReason.UnsupportedArgument(
                                call.name,
                                0,
                                resolved.detail,
                            )
                        }
                    }
                }
                else -> listOf(call).validateStaticArguments()
            }
            if (reason != null) return reason
        }
        return null
    }

    private fun List<RawCall>.requiresDumbModeSuppression(): Boolean = any { call ->
        call.name in setOf(
            "areRecords",
            "areNotRecords",
            "beRecords",
            "notBeRecords",
            "areMetaAnnotatedWith",
            "areNotMetaAnnotatedWith",
            "beMetaAnnotatedWith",
            "notBeMetaAnnotatedWith",
            "haveModifier",
            "notHaveModifier",
        ) ||
            (call.name == "resideInAnyPackage" && call.arguments.any { it !is RawArgument.StringLiteral })
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

    private data class IndexedRawCall(
        val index: Int,
        val call: RawCall,
    )

    private fun List<RawCall>.withIndexes(startIndex: Int = 0): List<IndexedRawCall> = mapIndexed { offset, call ->
        IndexedRawCall(startIndex + offset, call)
    }

    private fun <T> StaticArgumentResult<T>.resolvedOrNull(): T? = (this as? StaticArgumentResult.Resolved)?.value

    private fun List<IndexedRawCall>.classPredicate(
        context: PsiExpression,
        factResolver: StaticClassFactResolver,
    ): PredicateExpr? {
        if (firstOrNull()?.call?.name != "classes") return null
        val remaining = drop(1)
        if (remaining.isEmpty()) return PredicateExpr.All
        if (remaining.first().call.name != "that") return null
        val predicateCalls = remaining.drop(1).takeIf { it.isNotEmpty() } ?: return null

        var expression: PredicateExpr? = null
        var pendingOperator: String? = null
        predicateCalls.forEach { call ->
            if (call.call.name == "and" || call.call.name == "or") {
                if (expression == null || pendingOperator != null) return null
                pendingOperator = call.call.name
            } else {
                if (expression != null && pendingOperator == null) return null
                val leaf = call.classPredicateLeaf(context, factResolver) ?: return null
                expression = expression.appendPredicate(leaf, pendingOperator)
                pendingOperator = null
            }
        }
        return expression.takeIf { pendingOperator == null }
    }

    private fun IndexedRawCall.classPredicateLeaf(
        context: PsiExpression,
        factResolver: StaticClassFactResolver,
    ): PredicateExpr? = when (call.name) {
        "areAnnotatedWith" -> call.staticQualifiedType(context)?.let(PredicateExpr::AreAnnotatedWith)
        "areNotAnnotatedWith" -> call.staticQualifiedType(context)?.let(PredicateExpr::AreNotAnnotatedWith)
        "areMetaAnnotatedWith" -> call.staticallyResolvableType(context)
            ?.let { PredicateExpr.AreMetaAnnotatedWith(it, expected = true) }
        "areNotMetaAnnotatedWith" -> call.staticallyResolvableType(context)
            ?.let { PredicateExpr.AreMetaAnnotatedWith(it, expected = false) }
        "resideInAPackage" -> call.supportedPackagePatterns()?.let(PredicateExpr::ResideInPackages)
        "resideInAnyPackage" -> resolvedPackagePatterns(factResolver)?.let(PredicateExpr::ResideInPackages)
        "haveSimpleNameEndingWith" -> call.stringArgs.singleOrNull()?.let(PredicateExpr::HaveSimpleNameEndingWith)
        "haveSimpleNameNotEndingWith" -> call.stringArgs.singleOrNull()?.let(PredicateExpr::HaveSimpleNameNotEndingWith)
        "areInterfaces" -> PredicateExpr.AreInterfaces(expected = true)
        "areNotInterfaces" -> PredicateExpr.AreInterfaces(expected = false)
        "areEnums" -> PredicateExpr.AreEnums(expected = true)
        "areNotEnums" -> PredicateExpr.AreEnums(expected = false)
        "areRecords" -> PredicateExpr.AreRecords(expected = true)
        "areNotRecords" -> PredicateExpr.AreRecords(expected = false)
        else -> null
    }

    private fun List<IndexedRawCall>.classCondition(
        context: PsiExpression,
        factResolver: StaticClassFactResolver,
    ): ConditionExpr? {
        if (isEmpty()) return null
        var expression: ConditionExpr? = null
        var expectsCondition = true
        forEach { call ->
            if (call.call.name == "andShould") {
                if (expectsCondition || expression == null) return null
                expectsCondition = true
            } else {
                if (!expectsCondition) return null
                val leaf = call.classConditionLeaf(context, factResolver) ?: return null
                expression = expression?.let { ConditionExpr.And(it, leaf) } ?: leaf
                expectsCondition = false
            }
        }
        return expression.takeIf { !expectsCondition }
    }

    private fun IndexedRawCall.classConditionLeaf(
        context: PsiExpression,
        factResolver: StaticClassFactResolver,
    ): ConditionExpr? = when (call.name) {
        "beAnnotatedWith" -> call.staticQualifiedType(context)?.let { ConditionExpr.BeAnnotatedWith(it, required = true) }
        "notBeAnnotatedWith" -> call.staticQualifiedType(context)?.let { ConditionExpr.BeAnnotatedWith(it, required = false) }
        "beMetaAnnotatedWith" -> call.staticallyResolvableType(context)
            ?.let { ConditionExpr.BeMetaAnnotatedWith(it, required = true) }
        "notBeMetaAnnotatedWith" -> call.staticallyResolvableType(context)
            ?.let { ConditionExpr.BeMetaAnnotatedWith(it, required = false) }
        "resideInAPackage" -> call.supportedPackagePatterns()?.let(ConditionExpr::ResideInPackages)
        "resideInAnyPackage" -> resolvedPackagePatterns(factResolver)?.let(ConditionExpr::ResideInPackages)
        "haveSimpleNameEndingWith" -> call.stringArgs.singleOrNull()?.let {
            ConditionExpr.HaveSimpleNameEndingWith(it, required = true)
        }
        "haveSimpleNameNotEndingWith" -> call.stringArgs.singleOrNull()?.let {
            ConditionExpr.HaveSimpleNameEndingWith(it, required = false)
        }
        "beInterfaces" -> ConditionExpr.BeInterfaces(required = true)
        "notBeInterfaces" -> ConditionExpr.BeInterfaces(required = false)
        "beEnums" -> ConditionExpr.BeEnums(required = true)
        "notBeEnums" -> ConditionExpr.BeEnums(required = false)
        "beRecords" -> ConditionExpr.BeRecords(required = true)
        "notBeRecords" -> ConditionExpr.BeRecords(required = false)
        "haveModifier" -> factResolver.modifier(index).resolvedOrNull()
            ?.let { ConditionExpr.HaveModifier(it, required = true) }
        "notHaveModifier" -> factResolver.modifier(index).resolvedOrNull()
            ?.let { ConditionExpr.HaveModifier(it, required = false) }
        "beAssignableTo" -> call.staticallyResolvableType(context)?.let(ConditionExpr::BeAssignableTo)
        else -> null
    }

    private fun List<RawCall>.memberPredicate(
        context: PsiExpression,
        subject: MemberSubjectKind,
    ): MemberPredicateExpr? {
        if (firstOrNull()?.name !in setOf("noFields", "methods", "noMethods", "constructors")) return null
        if (size == 1) return MemberPredicateExpr.All
        if (getOrNull(1)?.name != "that") return null
        val predicateCalls = drop(2)
        if (predicateCalls.isEmpty()) return null

        var expression: MemberPredicateExpr? = null
        var pendingOperator: String? = null
        var encounteredDeclaringClass = false
        var index = 0
        while (index < predicateCalls.size) {
            val call = predicateCalls[index]
            if (call.name == "and" || call.name == "or") {
                if (expression == null || pendingOperator != null) return null
                pendingOperator = call.name
                index += 1
                continue
            }
            if (expression != null && pendingOperator == null) return null
            val leaf: MemberPredicateExpr
            if (call.name == "areDeclaredInClassesThat") {
                if (encounteredDeclaringClass) return null
                val classFact = predicateCalls.getOrNull(index + 1)?.memberDeclaringClassPredicateLeaf(context) ?: return null
                leaf = MemberPredicateExpr.DeclaredInClasses(classFact)
                encounteredDeclaringClass = true
                index += 1
            } else {
                if (encounteredDeclaringClass) return null
                if (subject == MemberSubjectKind.Constructors) return null
                leaf = call.memberPredicateLeaf(context) ?: return null
            }
            expression = expression.appendMemberPredicate(leaf, pendingOperator)
            pendingOperator = null
            index += 1
        }
        return expression?.takeIf { pendingOperator == null }
    }

    private fun RawCall.memberPredicateLeaf(context: PsiExpression): MemberPredicateExpr? = when (name) {
        "areAnnotatedWith" -> staticallyResolvableType(context)?.let { MemberPredicateExpr.IsAnnotatedWith(it, metaAnnotated = false) }
        "areMetaAnnotatedWith" -> staticallyResolvableType(context)?.let { MemberPredicateExpr.IsAnnotatedWith(it, metaAnnotated = true) }
        else -> null
    }

    private fun RawCall.memberDeclaringClassPredicateLeaf(context: PsiExpression): PredicateExpr? = when (name) {
        "areAnnotatedWith" -> staticallyResolvableType(context)?.let(PredicateExpr::AreAnnotatedWith)
        "areNotAnnotatedWith" -> staticallyResolvableType(context)?.let(PredicateExpr::AreNotAnnotatedWith)
        "areMetaAnnotatedWith" -> staticallyResolvableType(context)?.let { PredicateExpr.AreMetaAnnotatedWith(it, true) }
        "areAssignableTo" -> staticallyResolvableType(context)?.let { PredicateExpr.AreAssignableTo(it, true) }
        "implement" -> staticallyResolvableType(context)?.let { PredicateExpr.Implement(it, true) }
        "resideInAPackage", "resideInAnyPackage" -> supportedPackagePatterns()?.let(PredicateExpr::ResideInPackages)
        "haveSimpleNameEndingWith" -> stringArgs.singleOrNull()?.let(PredicateExpr::HaveSimpleNameEndingWith)
        "haveSimpleNameNotEndingWith" -> stringArgs.singleOrNull()?.let(PredicateExpr::HaveSimpleNameNotEndingWith)
        "areInterfaces" -> PredicateExpr.AreInterfaces(expected = true)
        "areNotInterfaces" -> PredicateExpr.AreInterfaces(expected = false)
        "areEnums" -> PredicateExpr.AreEnums(expected = true)
        "areNotEnums" -> PredicateExpr.AreEnums(expected = false)
        else -> null
    }

    private fun MemberPredicateExpr?.appendMemberPredicate(
        next: MemberPredicateExpr,
        operator: String?,
    ): MemberPredicateExpr = when {
        this == null -> next
        operator == "or" -> MemberPredicateExpr.Or(this, next)
        else -> MemberPredicateExpr.And(this, next)
    }

    private fun List<RawCall>.memberCondition(
        context: PsiExpression,
        subject: MemberSubjectKind,
        polarity: RulePolarity,
    ): MemberConditionExpr? {
        if (isEmpty()) return null
        var expression: MemberConditionExpr? = null
        var expectsCondition = true
        var pendingOperator: String? = null
        forEach { call ->
            if (call.name == "andShould" || call.name == "orShould") {
                if (call.name == "orShould" && polarity != RulePolarity.NEGATIVE) return null
                if (expectsCondition || expression == null) return null
                pendingOperator = call.name
                expectsCondition = true
            } else {
                if (!expectsCondition) return null
                val leaf = call.memberConditionLeaf(context, subject, polarity) ?: return null
                expression = expression?.let {
                    if (pendingOperator == "orShould") MemberConditionExpr.Or(it, leaf) else MemberConditionExpr.And(it, leaf)
                } ?: leaf
                pendingOperator = null
                expectsCondition = false
            }
        }
        return expression.takeIf { !expectsCondition }
    }

    private fun RawCall.memberConditionLeaf(
        context: PsiExpression,
        subject: MemberSubjectKind,
        polarity: RulePolarity,
    ): MemberConditionExpr? = when (name) {
        "bePrivate" -> if (subject == MemberSubjectKind.Constructors) {
            MemberConditionExpr.BePrivate
        } else {
            MemberConditionExpr.HaveModifier("private", true).takeIf { polarity == RulePolarity.NEGATIVE }
        }
        "notBePrivate" -> MemberConditionExpr.HaveModifier("private", false).takeIf { polarity == RulePolarity.NEGATIVE }
        "bePublic" -> MemberConditionExpr.HaveModifier("public", true).takeIf { polarity == RulePolarity.NEGATIVE }
        "notBePublic" -> MemberConditionExpr.HaveModifier("public", false).takeIf { polarity == RulePolarity.NEGATIVE }
        "beProtected" -> MemberConditionExpr.HaveModifier("protected", true).takeIf { polarity == RulePolarity.NEGATIVE }
        "notBeProtected" -> MemberConditionExpr.HaveModifier("protected", false).takeIf { polarity == RulePolarity.NEGATIVE }
        "bePackagePrivate" -> MemberConditionExpr.HaveModifier("package-private", true).takeIf { polarity == RulePolarity.NEGATIVE }
        "notBePackagePrivate" -> MemberConditionExpr.HaveModifier("package-private", false).takeIf { polarity == RulePolarity.NEGATIVE }
        "beStatic" -> if (subject == MemberSubjectKind.Methods) MemberConditionExpr.BeStatic else MemberConditionExpr.HaveModifier("static", true).takeIf { subject == MemberSubjectKind.Fields }
        "notBeStatic" -> MemberConditionExpr.HaveModifier("static", false).takeIf { polarity == RulePolarity.NEGATIVE }
        "beFinal" -> MemberConditionExpr.HaveModifier("final", true).takeIf { polarity == RulePolarity.NEGATIVE }
        "notBeFinal" -> MemberConditionExpr.HaveModifier("final", false).takeIf { polarity == RulePolarity.NEGATIVE }
        "beAnnotatedWith" -> staticallyResolvableType(context)?.let { MemberConditionExpr.BeAnnotatedWith(it, false, true) }.takeIf { polarity == RulePolarity.NEGATIVE }
        "notBeAnnotatedWith" -> staticallyResolvableType(context)?.let { MemberConditionExpr.BeAnnotatedWith(it, false, false) }.takeIf { polarity == RulePolarity.NEGATIVE }
        "beMetaAnnotatedWith" -> staticallyResolvableType(context)?.let { MemberConditionExpr.BeAnnotatedWith(it, true, true) }.takeIf { polarity == RulePolarity.NEGATIVE }
        "notBeMetaAnnotatedWith" -> staticallyResolvableType(context)?.let { MemberConditionExpr.BeAnnotatedWith(it, true, false) }.takeIf { polarity == RulePolarity.NEGATIVE }
        "haveName" -> stringArgs.singleOrNull()?.let { MemberConditionExpr.HaveName(it, true) }.takeIf { polarity == RulePolarity.NEGATIVE }
        "notHaveName" -> stringArgs.singleOrNull()?.let { MemberConditionExpr.HaveName(it, false) }.takeIf { polarity == RulePolarity.NEGATIVE }
        "haveNameMatching" -> stringArgs.singleOrNull()?.takeIf(::isValidRegex)?.let { MemberConditionExpr.HaveNameMatching(it, true) }.takeIf { polarity == RulePolarity.NEGATIVE }
        "notHaveNameMatching" -> stringArgs.singleOrNull()?.takeIf(::isValidRegex)?.let { MemberConditionExpr.HaveNameMatching(it, false) }.takeIf { polarity == RulePolarity.NEGATIVE }
        "haveRawReturnType" -> staticallyResolvableType(context)
            ?.takeIf { subject == MemberSubjectKind.Methods }
            ?.let(MemberConditionExpr::HaveRawReturnType)
        else -> null
    }

    private fun isValidRegex(pattern: String): Boolean = runCatching { Regex(pattern) }.isSuccess

    private fun RawCall.staticQualifiedType(context: PsiExpression): String? = stringArgs.singleOrNull()?.takeIf { it.contains('.') }
        ?: arguments.singleOrNull()
            ?.let { it as? RawArgument.ClassLiteral }
            ?.resolvedQualifiedName
            ?.let { qualifyClassLiteral(it, context) }

    private fun RawCall.supportedPackagePatterns(): List<String>? = stringArgs.takeIf { patterns ->
        patterns.isNotEmpty() && patterns.all(PackagePattern::isSupported)
    }

    private fun IndexedRawCall.resolvedPackagePatterns(
        factResolver: StaticClassFactResolver,
    ): List<String>? = if (call.arguments.all { it is RawArgument.StringLiteral }) {
        call.supportedPackagePatterns()
    } else {
        factResolver.packagePatterns(index).resolvedOrNull()
            ?.takeIf { patterns -> patterns.all(PackagePattern::isSupported) }
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
                "areMetaAnnotatedWith",
                "areNotMetaAnnotatedWith",
                "beMetaAnnotatedWith",
                "notBeMetaAnnotatedWith",
                "beAssignableTo",
            ) &&
                if (it.name in setOf(
                        "beAssignableTo",
                        "areMetaAnnotatedWith",
                        "areNotMetaAnnotatedWith",
                        "beMetaAnnotatedWith",
                        "notBeMetaAnnotatedWith",
                    )
                ) {
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

    private fun List<RawCall>.memberFallbackReason(context: PsiExpression): UnsupportedReason {
        val typeCall = firstOrNull {
            it.name in setOf(
                "areAnnotatedWith", "areMetaAnnotatedWith", "areNotAnnotatedWith", "areNotMetaAnnotatedWith",
                "beAnnotatedWith", "notBeAnnotatedWith", "beMetaAnnotatedWith", "notBeMetaAnnotatedWith",
                "areAssignableTo", "areNotAssignableTo", "implement", "doNotImplement", "haveRawReturnType",
            ) &&
                it.staticQualifiedType(context) == null
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
        condition: ConditionExpr = ConditionExpr.Leaf(
            calls.dropAfterShould().withoutTrailingBecauseCall().joinToString(".") { it.name }.ifBlank { "unknown" },
        ),
    ): RuleDescriptor = RuleDescriptor(
        subject = calls.subjectKind(),
        sourcePointer = source.fieldPointer,
        scope = source.analyzeScope,
        predicate = calls.predicateExpr(source.initializer)
            ?: PredicateExpr.Leaf(calls.takeUntilShould().joinToString(".") { it.name }.ifBlank { "unknown" }),
        condition = condition,
        reason = calls.reason(),
        supportStatus = SupportStatus.Unsupported(reason),
        polarity = calls.rootPolarity(),
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
            polarity = calls.rootPolarity(),
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
            polarity = calls.rootPolarity(),
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
        is NoClassesCodeAccessRule -> RuleDescriptor(
            subject = SubjectKind.Classes,
            sourcePointer = sourcePointer,
            scope = analyzeScope,
            predicate = PredicateExpr.All,
            condition = condition,
            reason = reason,
            supportStatus = SupportStatus.Supported,
        )
        is MemberConventionRule -> RuleDescriptor(
            subject = when (subject) {
                MemberSubjectKind.Fields -> SubjectKind.Fields
                MemberSubjectKind.Methods -> SubjectKind.Methods
                MemberSubjectKind.Constructors -> SubjectKind.Constructors
            },
            sourcePointer = sourcePointer,
            scope = analyzeScope,
            predicate = PredicateExpr.Leaf(predicate.display()),
            condition = ConditionExpr.Leaf(condition.display()),
            reason = reason,
            supportStatus = SupportStatus.Supported,
            polarity = polarity,
        )
    }

    private fun List<RawCall>.rootPolarity(): RulePolarity = if (firstOrNull()?.name in setOf("noClasses", "noFields", "noMethods")) {
        RulePolarity.NEGATIVE
    } else {
        RulePolarity.POSITIVE
    }

    private fun MemberPredicateExpr.display(): String = when (this) {
        MemberPredicateExpr.All -> "all"
        is MemberPredicateExpr.IsAnnotatedWith -> {
            val name = if (metaAnnotated) "areMetaAnnotatedWith" else "areAnnotatedWith"
            "$name($qualifiedName)"
        }
        is MemberPredicateExpr.DeclaredInClasses -> "areDeclaredInClassesThat(${predicate.display()})"
        is MemberPredicateExpr.And -> "(${left.display()} AND ${right.display()})"
        is MemberPredicateExpr.Or -> "(${left.display()} OR ${right.display()})"
    }

    private fun MemberConditionExpr.display(): String = when (this) {
        MemberConditionExpr.BePrivate -> "bePrivate"
        MemberConditionExpr.BeStatic -> "beStatic"
        is MemberConditionExpr.HaveRawReturnType -> "haveRawReturnType($qualifiedName)"
        is MemberConditionExpr.BeAnnotatedWith -> "${if (required) "be" else "notBe"}${if (metaAnnotated) "Meta" else ""}AnnotatedWith($qualifiedName)"
        is MemberConditionExpr.HaveModifier -> "${if (required) "be" else "notBe"}${modifier.replaceFirstChar(Char::uppercase)}"
        is MemberConditionExpr.HaveName -> "${if (required) "haveName" else "notHaveName"}($name)"
        is MemberConditionExpr.HaveNameMatching -> "${if (required) "haveNameMatching" else "notHaveNameMatching"}($pattern)"
        is MemberConditionExpr.And -> "(${left.display()} AND ${right.display()})"
        is MemberConditionExpr.Or -> "(${left.display()} OR ${right.display()})"
    }

    private fun PredicateExpr.display(): String = when (this) {
        PredicateExpr.All -> "all"
        is PredicateExpr.Leaf -> predicate
        is PredicateExpr.AreAnnotatedWith -> "areAnnotatedWith($qualifiedName)"
        is PredicateExpr.AreNotAnnotatedWith -> "areNotAnnotatedWith($qualifiedName)"
        is PredicateExpr.AreMetaAnnotatedWith -> "${if (expected) "are" else "areNot"}MetaAnnotatedWith($qualifiedName)"
        is PredicateExpr.AreAssignableTo -> "${if (expected) "are" else "areNot"}AssignableTo($qualifiedName)"
        is PredicateExpr.Implement -> "${if (expected) "implement" else "doNotImplement"}($qualifiedName)"
        is PredicateExpr.ResideInPackages -> "resideInPackages(${patterns.joinToString()})"
        is PredicateExpr.HaveSimpleNameEndingWith -> "haveSimpleNameEndingWith($suffix)"
        is PredicateExpr.HaveSimpleNameNotEndingWith -> "haveSimpleNameNotEndingWith($suffix)"
        is PredicateExpr.AreInterfaces -> if (expected) "areInterfaces" else "areNotInterfaces"
        is PredicateExpr.AreEnums -> if (expected) "areEnums" else "areNotEnums"
        is PredicateExpr.AreRecords -> if (expected) "areRecords" else "areNotRecords"
        is PredicateExpr.And -> "(${left.display()} AND ${right.display()})"
        is PredicateExpr.Or -> "(${left.display()} OR ${right.display()})"
    }

    private fun List<RawCall>.subjectKind(): SubjectKind = when (firstOrNull()?.name) {
        "classes", "noClasses" -> SubjectKind.Classes
        "theClass" -> SubjectKind.SingleClass
        "members" -> SubjectKind.Members
        "fields", "noFields" -> SubjectKind.Fields
        "codeUnits" -> SubjectKind.CodeUnits
        "constructors" -> SubjectKind.Constructors
        "methods", "noMethods" -> SubjectKind.Methods
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
        firstOrNull()?.name !in ARCHUNIT_SUBJECT_ENTRY_POINTS ->
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
