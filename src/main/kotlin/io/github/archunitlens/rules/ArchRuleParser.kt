package io.github.archunitlens.rules

import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile

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
        private val handlers = listOf(
            ::parsePackageDependencyBan,
            ::parseClassNameSuffix,
            ::parseForbiddenAnnotation,
            ::parseAnnotationExclusivity,
            ::parseInterfaceNaming,
            ::parseClassMetaAnnotation,
            ::parseMethodMetaAnnotation,
        )

        fun normalize(
            source: ArchRuleSource,
            calls: List<RawCall>,
        ): DiscoveredArchRule {
            val liveRule = handlers.firstNotNullOfOrNull { it(source, calls) }

            return DiscoveredArchRule(
                ruleName = source.ruleName,
                descriptor = liveRule?.toDescriptor(calls, source)
                    ?: unsupportedDescriptor(source, calls),
                liveRule = liveRule,
            )
        }
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

    private fun unsupportedDescriptor(
        source: ArchRuleSource,
        calls: List<RawCall>,
    ): RuleDescriptor = RuleDescriptor(
        subject = calls.subjectKind(),
        sourcePointer = source.fieldPointer,
        scope = source.analyzeScope,
        predicate = calls.predicateExpr(source.initializer)
            ?: PredicateExpr.Leaf(calls.takeUntilShould().joinToString(".") { it.name }.ifBlank { "unknown" }),
        condition = ConditionExpr.Leaf(calls.dropAfterShould().joinToString(".") { it.name }.ifBlank { "unknown" }),
        reason = calls.reason(),
        supportStatus = SupportStatus.Unsupported(calls.unsupportedReason()),
    )

    private fun LiveArchRule.toDescriptor(
        calls: List<RawCall>,
        source: ArchRuleSource,
    ): RuleDescriptor = when (this) {
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
        any { it.name == "resideInAnyPackage" } -> UnsupportedReason.ResideInAnyPackage
        any { it.name == "notBeMetaAnnotatedWith" } -> UnsupportedReason.CustomOrMetaAnnotationPredicates
        firstOrNull()?.name !in setOf("classes", "noClasses", "theClass", "members", "fields", "codeUnits", "constructors", "methods") ->
            UnsupportedReason.UnsupportedEntryPoint(firstOrNull()?.name ?: "unknown")
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
