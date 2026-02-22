package com.squareup.test.lib

import com.squareup.featureflags.BooleanFeatureFlag
import com.squareup.featureflags.anvil.ContributesFeatureFlag
import com.squareup.featureflags.anvil.Date
import com.squareup.featureflags.anvil.Month.April

@ContributesFeatureFlag(description = "Lib flag", removeBy = Date(April, 1, 2030))
object LibFeatureFlag : BooleanFeatureFlag("lib-flag")
