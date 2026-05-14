package com.test

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot
import dev.zacsweers.metro.SingleIn

class Dependency(val value: String)

@SingleIn(Unit::class)
@ContributesRobot(Unit::class)
class AbcRobot(val dependency: Dependency) : ScreenRobot<AbcRobot>()

@DependencyGraph(Unit::class)
@SingleIn(Unit::class)
interface MyGraph {
  val abcRobot: AbcRobot

  @Provides fun provideDependency(): Dependency = Dependency("OK")
}

fun box(): String {
  val contributionClass =
    AbcRobot::class.java.declaredClasses.first { it.simpleName == "RobotContribution" }
  val providerMethod =
    contributionClass.declaredMethods.first { it.name == "provideAbcRobotComponent" }
  assertNotNull(providerMethod.getAnnotation(SingleIn::class.java))

  val graph = createGraph<MyGraph>()
  assertTrue(graph.abcRobot === graph.abcRobot)

  return graph.abcRobot.dependency.value
}
