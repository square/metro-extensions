package com.squareup.test.app.data

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot
import dev.zacsweers.metro.Inject

@Inject @ContributesRobot(Unit::class) class TestRobot : ScreenRobot<TestRobot>()
