// RENDER_DIAGNOSTICS_FULL_TEXT
package com.test

import com.squareup.featureflags.BooleanFeatureFlag
import com.squareup.featureflags.anvil.ContributesFeatureFlag
import com.squareup.featureflags.anvil.Date
import com.squareup.featureflags.anvil.Month.April

<!CONTRIBUTES_FEATURE_FLAG_ERROR!>@ContributesFeatureFlag(description = "My flag", removeBy = Date(April, 1, 2030))<!>
class NotAnObject : BooleanFeatureFlag("flagKey")
