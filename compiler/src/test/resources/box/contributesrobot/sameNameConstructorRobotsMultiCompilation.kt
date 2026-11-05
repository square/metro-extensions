// MODULE: libA
package com.example.featurea

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot

@ContributesRobot(Unit::class)
class SettingsRobot(val dependency: String) : ScreenRobot<SettingsRobot>()

// MODULE: libB
package com.example.featureb

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot

@ContributesRobot(Unit::class)
class SettingsRobot(val dependency: String) : ScreenRobot<SettingsRobot>()

// MODULE: main(libA, libB)
package com.example

@DependencyGraph(Unit::class)
interface RobotGraph {
  @Provides fun dependency(): String = "OK"
}

fun box(): String {
  val graph = createGraph<RobotGraph>()
  val first =
    graph::class.java.getMethod("getcom_example_featurea_SettingsRobotComponent").invoke(graph)
  val second =
    graph::class.java.getMethod("getcom_example_featureb_SettingsRobotComponent").invoke(graph)
  assertTrue(first is com.example.featurea.SettingsRobot)
  assertTrue(second is com.example.featureb.SettingsRobot)
  assertEquals("OK", first.dependency)
  assertEquals("OK", second.dependency)
  return "OK"
}
