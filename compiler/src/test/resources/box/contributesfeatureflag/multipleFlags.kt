package com.test

import com.squareup.dagger.AppScope
import com.squareup.featureflags.BooleanFeatureFlag
import com.squareup.featureflags.FeatureFlag
import com.squareup.featureflags.StringFeatureFlag
import com.squareup.featureflags.anvil.ContributesDynamicConfigurationFlag
import com.squareup.featureflags.anvil.ContributesFeatureFlag
import com.squareup.featureflags.anvil.Date
import com.squareup.featureflags.anvil.Month.April

@ContributesFeatureFlag(description = "Flag one", removeBy = Date(April, 1, 2030))
object FlagOne : BooleanFeatureFlag("flag-one")

@ContributesFeatureFlag(description = "Flag two", removeBy = Date(April, 1, 2030))
object FlagTwo : StringFeatureFlag("flag-two")

@ContributesDynamicConfigurationFlag(description = "Config flag")
object ConfigFlag : BooleanFeatureFlag("config-flag")

@DependencyGraph(AppScope::class)
interface MyGraph {
  val featureFlags: Set<FeatureFlag>
}

fun box(): String {
  val graph = createGraph<MyGraph>()
  val flags = graph.featureFlags
  assertEquals(3, flags.size)
  assertTrue(flags.contains(FlagOne))
  assertTrue(flags.contains(FlagTwo))
  assertTrue(flags.contains(ConfigFlag))
  return "OK"
}
