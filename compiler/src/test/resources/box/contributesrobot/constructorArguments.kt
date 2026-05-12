package com.test

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot

@Qualifier annotation class RobotDependency

class Dependency(val value: String)

@ContributesRobot(Unit::class)
class AbcRobot(@RobotDependency val dependency: Dependency) : ScreenRobot<AbcRobot>()

@DependencyGraph(Unit::class)
interface MyGraph {
  val abcRobot: AbcRobot

  @Provides @RobotDependency fun provideDependency(): Dependency = Dependency("OK")
}

fun box(): String {
  val graph = createGraph<MyGraph>()
  return graph.abcRobot.dependency.value
}
