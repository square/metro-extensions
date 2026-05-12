package com.test

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot

class Dependency(val value: String)

@Inject
@ContributesRobot(Unit::class)
class AbcRobot(val dependency: Dependency) : ScreenRobot<AbcRobot>()

@DependencyGraph(Unit::class)
interface MyGraph {
  @Provides fun provideDependency(): Dependency = Dependency("OK")
}

fun box(): String {
  val contributionClass =
    AbcRobot::class.java.declaredClasses.first { it.simpleName == "RobotContribution" }
  val providerMethod =
    contributionClass.declaredMethods.firstOrNull { it.name == "provideAbcRobotComponent" }
  assertNull(providerMethod)

  val graph = createGraph<MyGraph>()
  val robot =
    graph::class.java.getMethod("getcom_test_AbcRobotComponent").invoke(graph) as AbcRobot
  return robot.dependency.value
}
