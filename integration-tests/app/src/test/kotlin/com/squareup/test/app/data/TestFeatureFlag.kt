package com.squareup.test.app.data

import com.squareup.featureflags.BooleanFeatureFlag
import com.squareup.featureflags.anvil.ContributesFeatureFlag
import com.squareup.featureflags.anvil.Date
import com.squareup.featureflags.anvil.Month.April

@ContributesFeatureFlag(description = "Test flag", removeBy = Date(April, 1, 2030))
object TestFeatureFlag : BooleanFeatureFlag("test-flag")
