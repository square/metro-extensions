package com.squareup.test.app.data

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.dagger.AppScope
import com.squareup.instrumentation.robots.ScreenRobot
import dev.zacsweers.metro.Inject

@Inject @ContributesRobot(AppScope::class) class TestRobot : ScreenRobot<TestRobot>()
