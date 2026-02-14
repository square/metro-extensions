package com.squareup.metro.extensions.scoped

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
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.ClassId

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

    if (!implementsScoped(declaration, session)) {
      val fqName = declaration.classId.asSingleFqName()
      reporter.reportOn(
        annotation.source,
        SquareMetroExtensionsDiagnostics.CONTRIBUTES_MULTIBINDING_SCOPED_ERROR,
        "$fqName contributes a multibinding for the interface mortar.Scoped " +
          "to the dependency graph, but doesn't implement mortar.Scoped. " +
          "Did you forget to add the super type?",
      )
    }
  }

  private fun implementsScoped(declaration: FirClass, session: FirSession): Boolean {
    return declaration.superTypeRefs.any { superTypeRef ->
      val coneType = superTypeRef.coneType.fullyExpandedType(session)
      implementsScopedTransitive(coneType, session, mutableSetOf())
    }
  }

  private fun implementsScopedTransitive(
    type: ConeKotlinType,
    session: FirSession,
    visited: MutableSet<ClassId>,
  ): Boolean {
    val classSymbol = type.toRegularClassSymbol(session) ?: return false
    val classId = classSymbol.classId
    if (!visited.add(classId)) return false
    if (classId == ClassIds.SCOPED) return true

    return classSymbol.resolvedSuperTypeRefs.any { superTypeRef ->
      val superConeType = superTypeRef.coneType.fullyExpandedType(session)
      implementsScopedTransitive(superConeType, session, visited)
    }
  }
}
