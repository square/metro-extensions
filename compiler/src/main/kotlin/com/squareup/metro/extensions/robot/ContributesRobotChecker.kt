package com.squareup.metro.extensions.robot

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
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.types.coneType

/**
 * FIR checker that validates classes annotated with `@ContributesRobot` extend
 * `com.squareup.instrumentation.robots.ScreenRobot` or
 * `com.squareup.instrumentation.robots.compose.ComposeScreenRobot`.
 */
internal object ContributesRobotChecker : FirClassChecker(MppCheckerKind.Common) {

  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(declaration: FirClass) {
    declaration.source ?: return
    val session = context.session

    val annotation =
      declaration.annotations.firstOrNull { ann ->
        ann.toAnnotationClassId(session) == ContributesRobotIds.CONTRIBUTES_ROBOT_CLASS_ID
      } ?: return

    val extendsRobot =
      declaration.superTypeRefs.any { superTypeRef ->
        val coneType = superTypeRef.coneType.fullyExpandedType()
        hasTransitiveSupertype(coneType, session, ClassIds.ROBOT_FQ_NAMES)
      }

    if (!extendsRobot) {
      val fqName = declaration.classId.asSingleFqName()
      reporter.reportOn(
        annotation.source,
        SquareMetroExtensionsDiagnostics.CONTRIBUTES_ROBOT_ERROR,
        "$fqName contributes a robot to the Metro graph, but doesn't extend " +
          "${ClassIds.SCREEN_ROBOT.asSingleFqName()}, or " +
          "${ClassIds.COMPOSE_SCREEN_ROBOT.asSingleFqName()}.",
      )
      return
    }

    val constructors = declaration.constructors(session)
    if (constructors.size <= 1) return

    val hasInjectAnnotation =
      declaration.annotations.any { it.toAnnotationClassIdSafe(session) == ClassIds.INJECT } ||
        constructors.any { constructor ->
          constructor.resolvedCompilerAnnotationsWithClassIds.any {
            it.toAnnotationClassIdSafe(session) == ClassIds.INJECT
          }
        }

    if (!hasInjectAnnotation) {
      val fqName = declaration.classId.asSingleFqName()
      reporter.reportOn(
        annotation.source,
        SquareMetroExtensionsDiagnostics.CONTRIBUTES_ROBOT_ERROR,
        "$fqName contributes a robot to the Metro graph and has multiple constructors. " +
          "Annotate the robot class or the constructor Metro should use with @Inject.",
      )
    }
  }
}
