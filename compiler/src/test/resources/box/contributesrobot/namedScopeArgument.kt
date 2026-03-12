package com.test

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.compose.ComposeScreenRobot

// Regression test: named scope arguments (scope = ...) were previously ignored in the
// ContributesRobot bridge, which dropped this robot contribution from the graph.
@Inject @ContributesRobot(scope = Unit::class) class AbcRobot : ComposeScreenRobot<AbcRobot>()

@DependencyGraph(Unit::class)
interface MyGraph

fun box(): String {
  val graph = createGraph<MyGraph>()
  val method = graph::class.java.getMethod("getcom_test_AbcRobotComponent")
  val robot = method.invoke(graph)
  assertNotNull(robot)
  assertTrue(robot is AbcRobot, "Expected AbcRobot but got: $robot")
  return "OK"
}
