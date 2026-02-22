package com.squareup.featureflags

sealed interface FeatureFlagTarget {
  data object DeviceId : FeatureFlagTarget
}
