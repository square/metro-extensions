package com.squareup.test.demo.featurescope

import com.squareup.dagger.ActivityScope
import com.squareup.development.shell.DevelopmentAppComponent
import com.squareup.development.shell.DevelopmentApplication
import com.squareup.development.shell.login.screen.FeatureProvider
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Marker scope for the feature. */
sealed interface DemoFeatureScope

/** The feature's graph extension, scoped under ActivityScope. */
@SingleIn(DemoFeatureScope::class)
@GraphExtension(DemoFeatureScope::class)
interface DemoFeatureComponent {
  val featureProvider: FeatureProvider

  @GraphExtension.Factory
  @ContributesTo(ActivityScope::class)
  interface Factory {
    fun createDemoFeatureComponent(): DemoFeatureComponent
  }
}

/** Provides [FeatureProvider] at the feature scope. */
@ContributesTo(DemoFeatureScope::class)
interface DemoFeatureProviderModule {
  @Provides fun provideFeatureProvider(): FeatureProvider = DemoFeatureProviderImpl()
}

class DemoFeatureProviderImpl : FeatureProvider

/**
 * Uses `featureScope`/`featureComponent` to redirect [FeatureProvider] from [ActivityScope] to
 * [DemoFeatureScope] and replace the default login-screen component.
 */
@DevelopmentAppComponent(
  featureScope = DemoFeatureScope::class,
  featureComponent = DemoFeatureComponent::class,
)
class FeatureScopeDemoApp : DevelopmentApplication()
