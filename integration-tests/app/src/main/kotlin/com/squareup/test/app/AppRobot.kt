package com.squareup.test.app

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.compose.ComposeScreenRobot
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject

@Inject @ContributesRobot(AppScope::class) class AppRobot : ComposeScreenRobot<AppRobot>()
