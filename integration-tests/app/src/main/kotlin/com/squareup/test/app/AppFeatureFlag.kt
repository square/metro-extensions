package com.squareup.test.app

import com.squareup.featureflags.StringFeatureFlag
import com.squareup.featureflags.anvil.ContributesDynamicConfigurationFlag

@ContributesDynamicConfigurationFlag(description = "App config flag")
object AppFeatureFlag : StringFeatureFlag("app-flag")
