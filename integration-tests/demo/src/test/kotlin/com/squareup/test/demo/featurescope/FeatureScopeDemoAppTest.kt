package com.squareup.test.demo.featurescope

import android.app.Application
import com.squareup.development.shell.DevelopmentAppComponent
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Integration test verifying that `featureScope`/`featureComponent` parameters on
 * `@DevelopmentAppComponent` correctly generate:
 * 1. `FeatureLoginScreenComponent` — redirects `FeatureProvider` to the feature scope
 * 2. `NoopLoginScreenComponent` — replaces `ContributedDevelopmentLoginScreenComponent` at
 *    ActivityScope so `FeatureProvider` is no longer required there
 * 3. `FeatureModule` — replaces `DefaultFeatureModule` at ActivityScope
 *
 * Without these, the graph fails to compile because
 * `ContributedDevelopmentLoginScreenComponent.featureProvider()` demands `FeatureProvider` at
 * ActivityScope, but the demo app only provides it at `DemoFeatureScope`. The fact that these tests
 * compile at all proves the feature scope redirection works.
 */
class FeatureScopeDemoAppTest {

  @Test
  fun `generated MetroComponent can be created via factory`() {
    val factory = createGraphFactory<FeatureScopeDemoApp.MetroComponent.Factory>()
    val component = factory.create(Application())
    assertIs<FeatureScopeDemoApp.MetroComponent>(component)
  }

  @Test
  fun `factory implements DevelopmentAppComponent Factory`() {
    val factory = createGraphFactory<FeatureScopeDemoApp.MetroComponent.Factory>()
    assertIs<DevelopmentAppComponent.Factory>(factory)
  }

  @Test
  fun `provideGraphFactory returns a working factory`() {
    val app = FeatureScopeDemoApp()
    val factory = app.provideGraphFactory()
    assertIs<DevelopmentAppComponent.Factory>(factory)
    val component = factory.create(Application())
    assertNotNull(component)
  }
}
