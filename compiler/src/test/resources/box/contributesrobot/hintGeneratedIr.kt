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
  // The hint class name follows the pattern: metro.hints.<Package><Class><Scope>Kt
  val hintClass = try {
    Class.forName("metro.hints.ComTestAbcRobotRobotContributionUnitKt")
  } catch (e: ClassNotFoundException) {
    return "FAIL: Scope hint not generated for AbcRobot.RobotContribution"
  }

  // The hint class should have a function named "Unit" (the scope)
  val hintFunction = hintClass.methods.find { it.name == "Unit" }
    ?: return "FAIL: Hint function 'Unit' not found in ${hintClass.name}"

  return "OK"
}
