package com.test

import com.squareup.dagger.AppScope
import com.squareup.featureflags.BooleanFeatureFlag
import com.squareup.featureflags.FeatureFlag
import com.squareup.featureflags.anvil.ContributesDynamicConfigurationFlag

@ContributesDynamicConfigurationFlag(description = "Config flag")
object MyConfigFlag : BooleanFeatureFlag("config-flag-key")

@DependencyGraph(AppScope::class)
interface MyGraph {
  val featureFlags: Set<FeatureFlag>
}

fun box(): String {
  val graph = createGraph<MyGraph>()
  val flags = graph.featureFlags
  assertEquals(1, flags.size)
  assertTrue(flags.single() === MyConfigFlag)
  return "OK"
}
