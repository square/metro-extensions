// DUMP_IR
package com.test

import com.squareup.featureflags.BooleanFeatureFlag
import com.squareup.featureflags.anvil.ContributesFeatureFlag
import com.squareup.featureflags.anvil.Date
import com.squareup.featureflags.anvil.Month.April

@ContributesFeatureFlag(description = "My flag", removeBy = Date(April, 1, 2030))
object MyFeatureFlag : BooleanFeatureFlag("my-flag-key")
