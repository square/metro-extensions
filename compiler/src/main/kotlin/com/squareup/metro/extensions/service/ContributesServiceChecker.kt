package com.squareup.metro.extensions.service

import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.fir.SquareMetroExtensionsDiagnostics
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirArrayLiteral
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol

/**
 * FIR checker that validates classes annotated with `@ContributesService`:
 * - Must be an interface when `replaces` is empty (real service)
 * - Must have exactly one qualifier annotation (meta-annotated with `@javax.inject.Qualifier`)
 */
internal object ContributesServiceChecker : FirClassChecker(MppCheckerKind.Common) {

  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(declaration: FirClass) {
    declaration.source ?: return
    val session = context.session

    val annotation =
      declaration.annotations.firstOrNull { ann ->
        ann.toAnnotationClassId(session) == ContributesServiceIds.CONTRIBUTES_SERVICE_CLASS_ID
      } ?: return

    val hasReplaces = hasReplacesArgument(annotation)

    // Real services must be interfaces
    if (!hasReplaces && declaration.classKind != ClassKind.INTERFACE) {
      val fqName = declaration.classId.asSingleFqName()
      reporter.reportOn(
        annotation.source,
        SquareMetroExtensionsDiagnostics.CONTRIBUTES_SERVICE_ERROR,
        "$fqName must be an interface in order to use @ContributesService.",
      )
      return
    }

    // Real services must have exactly one qualifier
    if (!hasReplaces) {
      val qualifierCount = countQualifiers(declaration, session)
      when {
        qualifierCount == 0 -> {
          reporter.reportOn(
            annotation.source,
            SquareMetroExtensionsDiagnostics.CONTRIBUTES_SERVICE_ERROR,
            "A qualifier for Retrofit services is required. @RetrofitAuthenticated is the most " +
              "commonly used qualifier. For more options take a look at RetrofitAnnotations.kt.",
          )
        }
        qualifierCount > 1 -> {
          reporter.reportOn(
            annotation.source,
            SquareMetroExtensionsDiagnostics.CONTRIBUTES_SERVICE_ERROR,
            "No more than one qualifier is allowed.",
          )
        }
      }
    }
  }

  private fun hasReplacesArgument(annotation: org.jetbrains.kotlin.fir.expressions.FirAnnotation): Boolean {
    val annotationCall = annotation as? FirAnnotationCall ?: return false
    for (arg in annotationCall.argumentList.arguments) {
      if (arg is FirNamedArgumentExpression && arg.name.asString() == "replaces") {
        val expr = arg.expression
        if (expr is FirArrayLiteral) {
          return expr.argumentList.arguments.isNotEmpty()
        }
        return true
      }
    }
    return false
  }

  private fun countQualifiers(
    declaration: FirClass,
    session: org.jetbrains.kotlin.fir.FirSession,
  ): Int {
    return declaration.annotations.count { ann ->
      val annotationClassId = ann.toAnnotationClassIdSafe(session) ?: return@count false
      if (annotationClassId == ContributesServiceIds.CONTRIBUTES_SERVICE_CLASS_ID) return@count false
      val annotationSymbol =
        session.symbolProvider.getClassLikeSymbolByClassId(annotationClassId)
          as? FirClassSymbol<*> ?: return@count false
      annotationSymbol.resolvedCompilerAnnotationsWithClassIds.any {
        it.toAnnotationClassIdSafe(session) in ClassIds.QUALIFIER_CLASS_IDS
      }
    }
  }
}
