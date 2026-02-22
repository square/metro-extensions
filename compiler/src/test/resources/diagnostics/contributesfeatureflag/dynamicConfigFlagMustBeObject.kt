// RENDER_DIAGNOSTICS_FULL_TEXT
package com.test

import com.squareup.featureflags.BooleanFeatureFlag
import com.squareup.featureflags.anvil.ContributesDynamicConfigurationFlag

<!CONTRIBUTES_FEATURE_FLAG_ERROR!>@ContributesDynamicConfigurationFlag(description = "My flag")<!>
class NotAnObject : BooleanFeatureFlag("flagKey")
