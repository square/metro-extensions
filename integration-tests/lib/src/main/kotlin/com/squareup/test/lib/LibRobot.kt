package com.squareup.test.lib

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot
import dev.zacsweers.metro.Inject

@Inject @ContributesRobot(Unit::class) class LibRobot : ScreenRobot<LibRobot>()
