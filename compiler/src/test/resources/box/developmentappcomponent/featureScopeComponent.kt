// Verifies that featureScope/featureComponent parameters generate three additional types:
//
// 1. FeatureLoginScreenComponent - @ContributesTo(featureScope) extending
//    DevelopmentLoginScreenComponent, moving the FeatureProvider accessor to the feature scope
//
// 2. NoopLoginScreenComponent - @ContributesTo(ActivityScope, replaces =
//    [ContributedDevelopmentLoginScreenComponent]) removing the default from ActivityScope
//
// 3. FeatureModule - @ContributesTo(ActivityScope, replaces = [DefaultFeatureModule]) @Module
//    object providing the feature component class
//
// Without these, the graph would fail to compile because
// ContributedDevelopmentLoginScreenComponent requires FeatureProvider at ActivityScope,
// but the demo app provides it at the feature scope.

// GENERATE_CONTRIBUTION_HINTS_IN_FIR

// MODULE: deps
package com.squareup.dagger

abstract class ActivityScope private constructor()

// MODULE: deps2(deps)
package com.squareup.development.shell.login.screen

import com.squareup.dagger.ActivityScope

interface FeatureProvider

interface DevelopmentLoginScreenComponent {
  fun featureProvider(): FeatureProvider
}

@ContributesTo(ActivityScope::class)
interface ContributedDevelopmentLoginScreenComponent : DevelopmentLoginScreenComponent

// MODULE: deps3(deps)
package com.squareup.development.shell

@ContributesTo(com.squareup.dagger.ActivityScope::class)
interface DefaultFeatureModule {
  @Provides fun provideDefaultFeatureComponent(): DevelopmentFeatureScopeComponent? = null
}

annotation class DevelopmentFeatureScopeComponent

// MODULE: main(deps, deps2, deps3)
package com.test

import android.app.Application
import com.squareup.dagger.AppScope
import com.squareup.development.shell.DevelopmentAppComponent
import com.squareup.development.shell.DevelopmentApplication
import com.squareup.development.shell.login.screen.FeatureProvider

sealed interface FeatureScope

@GraphExtension(FeatureScope::class)
interface FeatureComponent {
  val featureProvider: FeatureProvider

  @GraphExtension.Factory
  @ContributesTo(com.squareup.dagger.ActivityScope::class)
  interface Factory {
    fun createFeatureComponent(): FeatureComponent
  }
}

@ContributesTo(FeatureScope::class)
interface FeatureProviderModule {
  @Provides fun provideFeatureProvider(): FeatureProvider = object : FeatureProvider {}
}

@DevelopmentAppComponent(
  featureScope = FeatureScope::class,
  featureComponent = FeatureComponent::class,
)
class MyApp : DevelopmentApplication()

fun box(): String {
  val factory = createGraphFactory<MyApp.MetroComponent.Factory>()
  val component = factory.create(Application())
  assertTrue(component is MyApp.MetroComponent)
  return "OK"
}
