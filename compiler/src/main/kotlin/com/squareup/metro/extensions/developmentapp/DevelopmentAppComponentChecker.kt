package com.squareup.metro.extensions.developmentapp

import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.fir.SquareMetroExtensionsDiagnostics
import com.squareup.metro.extensions.fir.hasTransitiveSupertype
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * FIR checker that validates classes annotated with `@DevelopmentAppComponent`.
 *
 * Validates:
 * - The annotated class must extend `DevelopmentApplication`
 * - If `featureComponent` is specified, `featureScope` must also be specified (and vice versa)
 */
internal object DevelopmentAppComponentChecker : FirClassChecker(MppCheckerKind.Common) {

  private val UNIT_CLASS_ID = ClassId(FqName("kotlin"), Name.identifier("Unit"))

  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(declaration: FirClass) {
    declaration.source ?: return
    val session = context.session

    val annotation =
      declaration.annotations.firstOrNull { ann ->
        ann.toAnnotationClassId(session) == ClassIds.DEVELOPMENT_APP_COMPONENT
      } ?: return

    val fqName = declaration.classId.asSingleFqName()

    // Must extend DevelopmentApplication
    val extendsDevelopmentApplication =
      declaration.superTypeRefs.any { superTypeRef ->
        hasTransitiveSupertype(
          superTypeRef.coneType,
          session,
          listOf(ClassIds.DEVELOPMENT_APPLICATION),
        )
      }

    if (!extendsDevelopmentApplication) {
      reporter.reportOn(
        annotation.source,
        SquareMetroExtensionsDiagnostics.DEVELOPMENT_APP_COMPONENT_ERROR,
        "$fqName is annotated with @DevelopmentAppComponent, but isn't extending " +
          "com.squareup.development.shell.DevelopmentApplication.",
      )
      return
    }

    // Validate featureScope / featureComponent consistency
    val annotationCall = annotation as? FirAnnotationCall ?: return
    val featureScope = extractClassArgument(annotationCall, "featureScope")
    val featureComponent = extractClassArgument(annotationCall, "featureComponent")

    if (featureComponent != null && featureScope == null) {
      reporter.reportOn(
        annotation.source,
        SquareMetroExtensionsDiagnostics.DEVELOPMENT_APP_COMPONENT_ERROR,
        "${declaration.classId.shortClassName} uses @DevelopmentAppComponent with " +
          "featureComponent ${featureComponent.shortClassName}, but no featureScope was provided.",
      )
    }
    if (featureScope != null && featureComponent == null) {
      reporter.reportOn(
        annotation.source,
        SquareMetroExtensionsDiagnostics.DEVELOPMENT_APP_COMPONENT_ERROR,
        "${declaration.classId.shortClassName} uses @DevelopmentAppComponent with " +
          "featureScope ${featureScope.shortClassName}, but no featureComponent was provided.",
      )
    }
  }

  /**
   * Extract a `KClass<*>` argument by name from the annotation. Returns `null` if the argument is
   * absent or `Unit::class` (the default value indicating "not specified").
   */
  private fun extractClassArgument(annotationCall: FirAnnotationCall, argName: String): ClassId? {
    val name = Name.identifier(argName)

    // Try argument mapping first
    val mappedExpr = annotationCall.argumentMapping.mapping[name]
    if (mappedExpr != null) {
      return extractClassIdFromExpr(mappedExpr)
    }

    // Fall back to argument list
    for (arg in annotationCall.argumentList.arguments) {
      if (arg is FirNamedArgumentExpression && arg.name == name) {
        return extractClassIdFromExpr(arg.expression)
      }
    }

    return null
  }

  /** Extract ClassId from a `Foo::class` expression, returning null for `Unit::class`. */
  private fun extractClassIdFromExpr(
    expr: org.jetbrains.kotlin.fir.expressions.FirExpression
  ): ClassId? {
    val getClassCall = expr as? FirGetClassCall ?: return null
    val inner = getClassCall.argumentList.arguments.firstOrNull() ?: return null
    val classId =
      when (inner) {
        is FirResolvedQualifier -> inner.classId
        else -> null
      } ?: return null
    // Treat Unit::class as "not specified"
    return if (classId == UNIT_CLASS_ID) null else classId
  }
}
