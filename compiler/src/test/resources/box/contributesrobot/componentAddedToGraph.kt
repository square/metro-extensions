package com.test

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot

@Inject @ContributesRobot(Unit::class) class AbcRobot : ScreenRobot<AbcRobot>()

@DependencyGraph(Unit::class)
interface MyGraph

fun box(): String {
  val graph = createGraph<MyGraph>()
  // Verify the generated RobotContribution interface is a supertype of the graph implementation.
  val robotContributionClass = AbcRobot::class.java.declaredClasses
    .first { it.simpleName == "RobotContribution" }
  assertTrue(
    robotContributionClass.isAssignableFrom(graph::class.java),
    "Expected graph to implement RobotContribution but it doesn't",
  )
  return "OK"
}
