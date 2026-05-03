package com.squareup.test.app.data

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject

@Inject @ContributesRobot(AppScope::class) class TestRobot : ScreenRobot<TestRobot>()
