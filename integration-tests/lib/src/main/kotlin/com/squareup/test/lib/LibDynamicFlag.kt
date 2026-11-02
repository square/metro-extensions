package com.squareup.test.lib

import com.squareup.featureflags.BooleanFeatureFlag
import com.squareup.featureflags.anvil.ContributesDynamicConfigurationFlag

@ContributesDynamicConfigurationFlag(description = "Library configuration flag")
object LibDynamicFlag : BooleanFeatureFlag("lib-dynamic-flag")
