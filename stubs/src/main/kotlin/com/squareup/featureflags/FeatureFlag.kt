package com.squareup.featureflags

import com.squareup.featureflags.RevertToDefault.Never

sealed interface FeatureFlag {
  val flagKey: String
  val flagTarget: FeatureFlagTarget
  val revertToDefault: RevertToDefault
  val prioritizeInBugsnag: Boolean
}

enum class RevertToDefault {
  Never
}

sealed interface FeatureFlagWithDefaultValue<T : Any> : FeatureFlag {
  val defaultValue: T
}

abstract class BooleanFeatureFlag(
  override val flagKey: String,
  override val flagTarget: FeatureFlagTarget = FeatureFlagTarget.DeviceId,
  override val defaultValue: Boolean = false,
  override val revertToDefault: RevertToDefault = Never,
  override val prioritizeInBugsnag: Boolean = false,
) : FeatureFlagWithDefaultValue<Boolean>

abstract class StringFeatureFlag(
  override val flagKey: String,
  override val flagTarget: FeatureFlagTarget = FeatureFlagTarget.DeviceId,
  override val defaultValue: String = "",
  override val revertToDefault: RevertToDefault = Never,
  override val prioritizeInBugsnag: Boolean = false,
) : FeatureFlagWithDefaultValue<String>
