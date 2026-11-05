// MODULE: lib
package com.example

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot

class Outer {
  @ContributesRobot(Unit::class)
  class SettingsRobot(val dependency: String) : ScreenRobot<SettingsRobot>()
}

@ContributesRobot(String::class)
class Outer_SettingsRobot(val dependency: String) : ScreenRobot<Outer_SettingsRobot>()

// A literal escape token must not collide with Outer.SettingsRobot in the same graph.
@ContributesRobot(Unit::class)
class Outer_dSettingsRobot(val dependency: String) : ScreenRobot<Outer_dSettingsRobot>()

// MODULE: main(lib)
package com.example

// Separate scopes keep the existing flattened accessor names out of the same graph.
@DependencyGraph(Unit::class)
interface NestedGraph {
  @Provides fun dependency(): String = "nested"
}

@DependencyGraph(String::class)
interface UnderscoreGraph {
  @Provides fun dependency(): String = "underscore"
}

fun box(): String {
  val nestedGraph = createGraph<NestedGraph>()
  val underscoreGraph = createGraph<UnderscoreGraph>()
  val accessor = "getcom_example_Outer_SettingsRobotComponent"
  val nested = nestedGraph::class.java.getMethod(accessor).invoke(nestedGraph)
  val underscore = underscoreGraph::class.java.getMethod(accessor).invoke(underscoreGraph)
  val literal =
    nestedGraph::class.java.getMethod("getcom_example_Outer_dSettingsRobotComponent")
      .invoke(nestedGraph)
  assertTrue(nested is Outer.SettingsRobot)
  assertTrue(underscore is Outer_SettingsRobot)
  assertTrue(literal is Outer_dSettingsRobot)
  assertEquals("nested", nested.dependency)
  assertEquals("underscore", underscore.dependency)
  assertEquals("nested", literal.dependency)

  fun providerName(robot: Class<*>): String =
    robot.declaredClasses.single { it.simpleName == "RobotContribution" }
      .declaredMethods.single { it.name.startsWith("provide") }.name

  assertNotEquals(
    providerName(Outer.SettingsRobot::class.java),
    providerName(Outer_SettingsRobot::class.java),
  )
  assertNotEquals(
    providerName(Outer.SettingsRobot::class.java),
    providerName(Outer_dSettingsRobot::class.java),
  )
  return "OK"
}
