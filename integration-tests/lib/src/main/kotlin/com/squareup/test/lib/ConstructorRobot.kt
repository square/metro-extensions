package com.squareup.test.lib

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot
import dev.zacsweers.metro.SingleIn

@ContributesRobot(BootstrapScope::class)
@SingleIn(BootstrapScope::class)
class ConstructorRobot(val dependency: ScopedDependency) : ScreenRobot<ConstructorRobot>()
