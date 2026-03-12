// MODULE: deps
package com.squareup.dagger

abstract class ActivityScope private constructor()

// MODULE: deps2(deps)
package com.squareup.development.shell.login.screen

interface DevelopmentLoginScreenComponent

@ContributesTo(com.squareup.dagger.ActivityScope::class)
interface ContributedDevelopmentLoginScreenComponent : DevelopmentLoginScreenComponent

// MODULE: deps3(deps)
package com.squareup.development.shell

@ContributesTo(com.squareup.dagger.ActivityScope::class)
interface DefaultFeatureModule

// MODULE: main(deps, deps2, deps3)
package com.test

import com.squareup.development.shell.DevelopmentAppComponent
import com.squareup.development.shell.DevelopmentApplication

sealed interface FeatureScope

interface FeatureComponent

@DevelopmentAppComponent(
  featureScope = FeatureScope::class,
  featureComponent = FeatureComponent::class,
)
class MyApp : DevelopmentApplication()
