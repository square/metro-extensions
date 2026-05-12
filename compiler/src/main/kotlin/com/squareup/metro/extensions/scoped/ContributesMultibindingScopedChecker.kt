package com.squareup.metro.extensions.scoped

import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.fir.SquareMetroExtensionsDiagnostics
import com.squareup.metro.extensions.fir.hasTransitiveSupertype
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.constructors
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.types.coneType

/**
 * FIR checker that validates classes annotated with `@ContributesMultibindingScoped` implement the
 * `mortar.Scoped` interface.
 */
internal object ContributesMultibindingScopedChecker : FirClassChecker(MppCheckerKind.Common) {

  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(declaration: FirClass) {
    declaration.source ?: return
    val session = context.session

    val annotation =
      declaration.annotations.firstOrNull { ann ->
        ann.toAnnotationClassId(session) ==
          ContributesMultibindingScopedIds.CONTRIBUTES_MULTIBINDING_SCOPED_CLASS_ID
      } ?: return

    val implementsScoped =
      declaration.superTypeRefs.any { superTypeRef ->
        val coneType = superTypeRef.coneType.fullyExpandedType()
        hasTransitiveSupertype(coneType, session, listOf(ClassIds.SCOPED))
      }

    if (!implementsScoped) {
      val fqName = declaration.classId.asSingleFqName()
      reporter.reportOn(
        annotation.source,
        SquareMetroExtensionsDiagnostics.CONTRIBUTES_MULTIBINDING_SCOPED_ERROR,
        "$fqName contributes a multibinding for the interface mortar.Scoped " +
          "to the dependency graph, but doesn't implement mortar.Scoped. " +
          "Did you forget to add the super type?",
      )
      return
    }

    validateConstructors(declaration, annotation)
  }

  context(context: CheckerContext, reporter: DiagnosticReporter)
  private fun validateConstructors(declaration: FirClass, annotation: FirAnnotation) {
    val constructors = declaration.constructors(context.session)
    if (constructors.size <= 1) return

    val hasInjectAnnotation =
      declaration.annotations.any {
        it.toAnnotationClassIdSafe(context.session) == ClassIds.INJECT
      } ||
        constructors.any { constructor ->
          constructor.resolvedCompilerAnnotationsWithClassIds.any {
            it.toAnnotationClassIdSafe(context.session) == ClassIds.INJECT
          }
        }

    if (!hasInjectAnnotation) {
      val fqName = declaration.classId.asSingleFqName()
      reporter.reportOn(
        annotation.source,
        SquareMetroExtensionsDiagnostics.CONTRIBUTES_MULTIBINDING_SCOPED_ERROR,
        "$fqName contributes a multibinding for mortar.Scoped to the Metro graph and has " +
          "multiple constructors. Annotate the scoped class or the constructor Metro should use " +
          "with @Inject.",
      )
    }
  }
}
