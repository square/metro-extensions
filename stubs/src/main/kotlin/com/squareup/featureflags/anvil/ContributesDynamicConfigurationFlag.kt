package com.squareup.featureflags.anvil

annotation class ContributesDynamicConfigurationFlag(
  val description: String,
  val fakeModeValues: Array<FlagValue> = [],
)
