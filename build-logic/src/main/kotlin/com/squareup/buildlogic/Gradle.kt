package com.squareup.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

val Project.ci: Boolean
  get() = providers.environmentVariable("CI").isPresent
val Project.libs
  get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
