// RENDER_DIAGNOSTICS_FULL_TEXT
package com.test

import com.squareup.development.shell.DevelopmentAppComponent
import com.squareup.development.shell.DevelopmentApplication

interface FeatureScope

<!DEVELOPMENT_APP_COMPONENT_ERROR!>@DevelopmentAppComponent(
  featureScope = FeatureScope::class
)<!>
class MyApp : DevelopmentApplication()
