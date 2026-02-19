package com.squareup.metro.extensions.scoped

import com.squareup.metro.extensions.ArgNames
import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.fir.SquareMetroExtensionsDiagnostics
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * FIR checker that validates scope consistency when a class replaces or excludes a class annotated
 * with `@ContributesMultibindingScoped`.
 *
 * For example, if `Service1` has `@ContributesMultibindingScoped(Int::class)` and `Service2` has
 * `@ContributesBinding(Unit::class, replaces = [Service1::class])`, the scopes don't match (Unit vs
 * Int) and an error should be reported.
 */
internal object ContributesMultibindingScopedReplacesChecker :
  FirClassChecker(MppCheckerKind.Common) {

  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(declaration: FirClass) {
    declaration.source ?: return
    val session = context.session

    for (annotation in declaration.annotations) {
      val annotationClassId = annotation.toAnnotationClassId(session) ?: continue

      when (annotationClassId) {
        in ClassIds.ANNOTATIONS_WITH_REPLACES -> {
          val replacedClassIds = extractReplacedClassIds(annotation, session)
          if (replacedClassIds.isEmpty()) continue
          val annotationScope = extractScopeFromAnnotation(annotation, session) ?: continue
          checkReplacedClasses(declaration, annotation, annotationScope, replacedClassIds, session)
        }
        ClassIds.DEPENDENCY_GRAPH -> {
          val excludedClassIds = extractExcludedClassIds(annotation, session)
          if (excludedClassIds.isEmpty()) continue
          val annotationScope = extractScopeFromAnnotation(annotation, session) ?: continue
          checkReplacedClasses(declaration, annotation, annotationScope, excludedClassIds, session)
        }
      }
    }
  }

  context(context: CheckerContext, reporter: DiagnosticReporter)
  private fun checkReplacedClasses(
    declaration: FirClass,
    annotation: FirAnnotation,
    annotationScope: ClassId,
    replacedClassIds: List<ClassId>,
    session: FirSession,
  ) {
    for (replacedClassId in replacedClassIds) {
      val replacedSymbol =
        session.symbolProvider.getClassLikeSymbolByClassId(replacedClassId)
          as? FirRegularClassSymbol ?: continue

      // Check if the replaced class has @ContributesMultibindingScoped
      val contributesMultibindingScopedAnnotation =
        replacedSymbol.resolvedAnnotationsWithArguments.firstOrNull { ann ->
          ann.toAnnotationClassIdSafe(session) ==
            ContributesMultibindingScopedIds.CONTRIBUTES_MULTIBINDING_SCOPED_CLASS_ID
        } ?: continue

      // Extract the scope from @ContributesMultibindingScoped
      val replacedScope =
        extractScopeFromAnnotation(contributesMultibindingScopedAnnotation, session) ?: continue

      if (annotationScope != replacedScope) {
        reporter.reportOn(
          annotation.source,
          SquareMetroExtensionsDiagnostics.CONTRIBUTES_MULTIBINDING_SCOPED_ERROR,
          "${declaration.classId.asSingleFqName()} is replacing / excluding " +
            "${replacedClassId.asSingleFqName()}, but it uses a different scope: " +
            "${annotationScope.asSingleFqName()} and not ${replacedScope.asSingleFqName()}. " +
            "You can only replace / exclude contributions with the same scope.",
        )
      }
    }
  }

  /**
   * Extracts the scope ClassId from the first argument (scope) of a Metro annotation.
   *
   * Works for `@ContributesBinding(SomeScope::class, ...)`, `@ContributesTo(SomeScope::class,
   * ...)`, `@DependencyGraph(SomeScope::class, ...)`, and
   * `@ContributesMultibindingScoped(SomeScope::class)`.
   */
  private fun extractScopeFromAnnotation(annotation: FirAnnotation, session: FirSession): ClassId? {
    val annotationCall = annotation as? FirAnnotationCall ?: return null
    val args = annotationCall.argumentList.arguments
    if (args.isEmpty()) return null

    // The scope is always the first argument
    val scopeArg =
      when (val firstArg = args[0]) {
        is FirNamedArgumentExpression ->
          if (firstArg.name == ArgNames.SCOPE) firstArg.expression else null
        else -> firstArg
      }

    return resolveClassIdFromExpression(scopeArg ?: return null, session)
  }

  /**
   * Extracts ClassIds from the `replaces` array argument of a Metro annotation.
   *
   * Handles both named (`replaces = [...]`) and positional arguments. The `replaces` parameter is
   * at index 2 for `@ContributesBinding`/`@ContributesIntoSet`/`@ContributesIntoMap` and index 1
   * for `@ContributesTo`.
   */
  private fun extractReplacedClassIds(
    annotation: FirAnnotation,
    session: FirSession,
  ): List<ClassId> {
    return extractClassIdsFromArrayArg(annotation, ArgNames.REPLACES, session)
  }

  /**
   * Extracts ClassIds from the `excludes` array argument of `@DependencyGraph`. The `excludes`
   * parameter is at index 2.
   */
  private fun extractExcludedClassIds(
    annotation: FirAnnotation,
    session: FirSession,
  ): List<ClassId> {
    return extractClassIdsFromArrayArg(annotation, ArgNames.EXCLUDES, session)
  }

  private fun extractClassIdsFromArrayArg(
    annotation: FirAnnotation,
    argName: Name,
    session: FirSession,
  ): List<ClassId> {
    // Try argument mapping first (resolved annotations)
    annotation.argumentMapping.mapping[argName]?.let { expr ->
      return extractClassIdsFromArrayExpression(expr, session)
    }

    // Fall back to FirAnnotationCall argument list
    val annotationCall = annotation as? FirAnnotationCall ?: return emptyList()
    for (arg in annotationCall.argumentList.arguments) {
      if (arg is FirNamedArgumentExpression && arg.name == argName) {
        return extractClassIdsFromArrayExpression(arg.expression, session)
      }
    }
    return emptyList()
  }

  private fun extractClassIdsFromArrayExpression(
    expr: FirExpression,
    session: FirSession,
  ): List<ClassId> {
    val arrayElements =
      when (expr) {
        is FirCall -> expr.argumentList.arguments
        else -> return emptyList()
      }
    return arrayElements.mapNotNull { element ->
      if (element is FirGetClassCall) {
        resolveClassIdFromGetClassCall(element, session)
      } else {
        null
      }
    }
  }

  private fun resolveClassIdFromExpression(expr: FirExpression, session: FirSession): ClassId? {
    return when (expr) {
      is FirGetClassCall -> resolveClassIdFromGetClassCall(expr, session)
      else -> null
    }
  }

  private fun resolveClassIdFromGetClassCall(
    getClassCall: FirGetClassCall,
    session: FirSession,
  ): ClassId? {
    val innerArg = getClassCall.argumentList.arguments.firstOrNull() ?: return null
    return when (innerArg) {
      is FirResolvedQualifier -> innerArg.classId
      is FirPropertyAccessExpression -> {
        val ref = innerArg.calleeReference
        if (ref is FirResolvedNamedReference && ref.resolvedSymbol is FirClassLikeSymbol<*>) {
          (ref.resolvedSymbol as FirClassLikeSymbol<*>).classId
        } else {
          val name = ref.name
          session.symbolProvider
            .getClassLikeSymbolByClassId(ClassId(FqName("kotlin"), name))
            ?.classId
        }
      }
      else -> null
    }
  }
}
