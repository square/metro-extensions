package com.example.featureb

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot

@ContributesRobot(Unit::class)
class SettingsRobot(val dependency: String) : ScreenRobot<SettingsRobot>()
