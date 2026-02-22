package com.test

import com.squareup.dagger.AppScope
import com.squareup.featureflags.BooleanFeatureFlag
import com.squareup.featureflags.FeatureFlag
import com.squareup.featureflags.anvil.ContributesFeatureFlag
import com.squareup.featureflags.anvil.Date
import com.squareup.featureflags.anvil.Month.April

@ContributesFeatureFlag(description = "My flag", removeBy = Date(April, 1, 2030))
object MyFeatureFlag : BooleanFeatureFlag("my-flag-key")

@DependencyGraph(AppScope::class)
interface MyGraph {
  val featureFlags: Set<FeatureFlag>
}

fun box(): String {
  val graph = createGraph<MyGraph>()
  val flags = graph.featureFlags
  assertEquals(1, flags.size)
  assertTrue(flags.single() === MyFeatureFlag)
  return "OK"
}
