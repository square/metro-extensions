package com.squareup.metro.extensions.service

import com.squareup.metro.extensions.ArgNames
import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.fir.SquareMetroExtensionsDiagnostics
import com.squareup.metro.extensions.fir.extractClassIdsFromArrayArg
import org.jetbrains.kotlin.descriptors.ClassKind
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
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol

/**
 * FIR checker that validates classes annotated with `@ContributesService`.
 *
 * **Real services** (no `replaces`):
 * - Must be an interface
 * - Must have exactly one qualifier annotation (e.g., `@RetrofitAuthenticated`)
 *
 * **Fake services** (has `replaces`): no additional validation (they are classes, not interfaces,
 * and the qualifier comes from the replaced service).
 */
internal object ContributesServiceChecker : FirClassChecker(MppCheckerKind.Common) {

  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(declaration: FirClass) {
    // Skip generated declarations (no source) and classes without our annotation.
    declaration.source ?: return
    val session = context.session

    val annotation =
      declaration.annotations.firstOrNull { ann ->
        ann.toAnnotationClassId(session) == ContributesServiceIds.CONTRIBUTES_SERVICE_CLASS_ID
      } ?: return

    val hasReplaces =
      extractClassIdsFromArrayArg(annotation, ArgNames.REPLACES, session).isNotEmpty()

    // Fake services skip all validation — they are classes (not interfaces) and inherit
    // their qualifier from the replaced service.
    if (hasReplaces) return

    // Real services must be interfaces (Retrofit service definitions).
    if (declaration.classKind != ClassKind.INTERFACE) {
      val fqName = declaration.classId.asSingleFqName()
      reporter.reportOn(
        annotation.source,
        SquareMetroExtensionsDiagnostics.CONTRIBUTES_SERVICE_ERROR,
        "$fqName must be an interface in order to use @ContributesService.",
      )
      return
    }

    // Real services must have exactly one qualifier to determine which ServiceCreator is injected.
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

  /**
   * Counts qualifier annotations on the class (annotations meta-annotated with `@Qualifier`),
   * excluding `@ContributesService` itself.
   */
  private fun countQualifiers(declaration: FirClass, session: FirSession): Int {
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
