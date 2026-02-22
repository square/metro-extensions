// RENDER_DIAGNOSTICS_FULL_TEXT
package com.test

import com.squareup.featureflags.anvil.ContributesDynamicConfigurationFlag

<!CONTRIBUTES_FEATURE_FLAG_ERROR!>@ContributesDynamicConfigurationFlag(description = "Not a flag")<!>
object NotAFeatureFlag
