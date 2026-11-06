package com.squareup.test.app

import com.example.featurea.SettingsRobot as FeatureARobot
import com.example.featureb.SettingsRobot as FeatureBRobot
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@DependencyGraph(Unit::class)
interface RobotGraph {
  @Provides fun dependency(): String = "shared dependency"
}

class CrossModuleRobotTest {
  @Test
  fun `same named constructor robots from compiled contributions can share a graph`() {
    // Both contributions come from the separately compiled library with redundant mirrors omitted.
    val graph = createGraph<RobotGraph>()
    val first = graph.getcom_example_featurea_SettingsRobotComponent()
    val second = graph.getcom_example_featureb_SettingsRobotComponent()
    assertIs<FeatureARobot>(first)
    assertIs<FeatureBRobot>(second)
    assertEquals("shared dependency", first.dependency)
    assertEquals("shared dependency", second.dependency)
  }
}
