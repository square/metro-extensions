// RENDER_DIAGNOSTICS_FULL_TEXT
package com.test

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot

<!CONTRIBUTES_ROBOT_ERROR!>@ContributesRobot(Unit::class)<!>
class AbcRobot() : ScreenRobot<AbcRobot>() {
  constructor(value: String) : this()
}
