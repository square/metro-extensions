// MODULE: lib
package com.test

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot

@Inject @ContributesRobot(Unit::class) class AbcRobot : ScreenRobot<AbcRobot>()

// MODULE: main(lib)
package com.test

fun box(): String {
  // Verify that the scope hint function was generated for the @ContributesTo interface.
  // Metro uses these hints to discover cross-module contributions.
  // The hint class name includes the fully qualified scope to avoid scope-name collisions.
  val hintClass = try {
    Class.forName("metro.hints.ComTestAbcRobotRobotContributionKotlin_UnitKt")
  } catch (e: ClassNotFoundException) {
    return "FAIL: Scope hint not generated for AbcRobot.RobotContribution"
  }

  val hintFunction = hintClass.methods.find { it.name == "kotlin_Unit" }
    ?: return "FAIL: Hint function 'kotlin_Unit' not found in ${hintClass.name}"

  return "OK"
}
