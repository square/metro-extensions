// RENDER_DIAGNOSTICS_FULL_TEXT
package com.test

import com.squareup.development.shell.DevelopmentAppComponent
import com.squareup.development.shell.DevelopmentApplication

interface FeatureComponent

<!DEVELOPMENT_APP_COMPONENT_ERROR!>@DevelopmentAppComponent(
  featureComponent = FeatureComponent::class
)<!>
class MyApp : DevelopmentApplication()
