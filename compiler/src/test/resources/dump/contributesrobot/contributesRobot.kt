// RUN_PIPELINE_TILL: BACKEND
package com.test

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot

class Dependency @Inject constructor()

@Inject @ContributesRobot(Unit::class)
class AbcRobot(val dependency: Dependency) : ScreenRobot<AbcRobot>()
