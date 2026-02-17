package com.test

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot

class Outer {
  @Inject @ContributesRobot(Unit::class) class AbcRobot : ScreenRobot<AbcRobot>()
}

@DependencyGraph(Unit::class)
interface MyGraph

fun box(): String {
  val graph = createGraph<MyGraph>()
  val method = graph::class.java.getMethod("getAbcRobot")
  val robot = method.invoke(graph)
  assertNotNull(robot)
  assertTrue(robot is Outer.AbcRobot, "Expected Outer.AbcRobot but got: $robot")
  return "OK"
}
