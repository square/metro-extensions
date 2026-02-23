package com.squareup.metro.extensions.featureflag

import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object ContributesFeatureFlagIds {
  val CONTRIBUTES_FEATURE_FLAG_CLASS_ID =
    ClassId(FqName("com.squareup.featureflags.anvil"), Name.identifier("ContributesFeatureFlag"))

  val CONTRIBUTES_DYNAMIC_CONFIGURATION_FLAG_CLASS_ID =
    ClassId(
      FqName("com.squareup.featureflags.anvil"),
      Name.identifier("ContributesDynamicConfigurationFlag"),
    )

  val ANNOTATION_CLASS_IDS =
    setOf(CONTRIBUTES_FEATURE_FLAG_CLASS_ID, CONTRIBUTES_DYNAMIC_CONFIGURATION_FLAG_CLASS_ID)

  private val CONTRIBUTES_FEATURE_FLAG_FQ_NAME =
    FqName("com.squareup.featureflags.anvil.ContributesFeatureFlag")

  private val CONTRIBUTES_DYNAMIC_CONFIGURATION_FLAG_FQ_NAME =
    FqName("com.squareup.featureflags.anvil.ContributesDynamicConfigurationFlag")

  val NESTED_INTERFACE_NAME = Name.identifier("FeatureFlagContribution")

  val PREDICATE =
    LookupPredicate.create {
      annotated(CONTRIBUTES_FEATURE_FLAG_FQ_NAME) or
        annotated(CONTRIBUTES_DYNAMIC_CONFIGURATION_FLAG_FQ_NAME)
    }
}
