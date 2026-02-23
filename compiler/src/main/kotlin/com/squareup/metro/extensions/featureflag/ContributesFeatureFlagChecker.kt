package com.squareup.metro.extensions.featureflag

import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.fir.SquareMetroExtensionsDiagnostics
import com.squareup.metro.extensions.fir.hasTransitiveSupertype
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.types.coneType

/**
 * FIR checker that validates classes annotated with `@ContributesFeatureFlag` or
 * `@ContributesDynamicConfigurationFlag`.
 *
 * Validates:
 * - The annotated class must be public
 * - The annotated class must be a Kotlin object
 * - The annotated class must implement `FeatureFlag`
 */
internal object ContributesFeatureFlagChecker : FirClassChecker(MppCheckerKind.Common) {

  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(declaration: FirClass) {
    declaration.source ?: return
    val session = context.session

    val annotation =
      declaration.annotations.firstOrNull { ann ->
        ann.toAnnotationClassId(session) in ContributesFeatureFlagIds.ANNOTATION_CLASS_IDS
      } ?: return

    val fqName = declaration.classId.asSingleFqName()

    // Must be public
    if (declaration.visibility != Visibilities.Public) {
      reporter.reportOn(
        annotation.source,
        SquareMetroExtensionsDiagnostics.CONTRIBUTES_FEATURE_FLAG_ERROR,
        "$fqName is not public. Feature flags must be public so that generated " +
          "contributions are visible when merging dependency graphs.",
      )
      return
    }

    // Must be a Kotlin object
    if (declaration.classKind != ClassKind.OBJECT) {
      reporter.reportOn(
        annotation.source,
        SquareMetroExtensionsDiagnostics.CONTRIBUTES_FEATURE_FLAG_ERROR,
        "$fqName is not a Kotlin object. Feature flags should not have any arguments and " +
          "should not be initialized more than once.",
      )
      return
    }

    // Must implement FeatureFlag
    val implementsFeatureFlag =
      declaration.superTypeRefs.any { superTypeRef ->
        hasTransitiveSupertype(superTypeRef.coneType, session, listOf(ClassIds.FEATURE_FLAG))
      }

    if (!implementsFeatureFlag) {
      reporter.reportOn(
        annotation.source,
        SquareMetroExtensionsDiagnostics.CONTRIBUTES_FEATURE_FLAG_ERROR,
        "$fqName contributes a feature flag to the dependency graph, but doesn't implement " +
          "com.squareup.featureflags.FeatureFlag.",
      )
    }
  }
}
