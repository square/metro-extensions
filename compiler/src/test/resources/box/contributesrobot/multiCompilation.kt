// MODULE: lib
package com.test

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot

@Inject @ContributesRobot(Unit::class) class AbcRobot : ScreenRobot<AbcRobot>()

// MODULE: main(lib)
package com.test

@DependencyGraph(Unit::class)
interface MyGraph

fun box(): String {
  val graph = createGraph<MyGraph>()
  val method = graph::class.java.getMethod("getAbcRobotContribution")
  val robot = method.invoke(graph)
  assertNotNull(robot)
  assertTrue(robot is AbcRobot, "Expected AbcRobot but got: $robot")
  return "OK"
}
