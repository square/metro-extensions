package com.squareup.buildlogic

import com.ncorti.ktfmt.gradle.KtfmtExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("unused")
class LibPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit =
    target.run {
      configureReleaseTask()
      configureKotlin()
      configureKtfmt()
      configureDependencySubstitution()
      configureTestTask()
    }

  private fun Project.configureReleaseTask() {
    val release = tasks.register("release")
    release.configure { task -> task.dependsOn("assemble", "test", "ktfmtCheck") }

    pluginManager.withPlugin(Plugins.BINARY_COMPAT_VALIDATOR) {
      release.configure { it.dependsOn("apiCheck") }
    }
  }

  private fun Project.configureKotlin() {
    pluginManager.apply(Plugins.KOTLIN_JVM)

    dependencies.add("implementation", dependencies.platform(libs.findLibrary("kotlin-bom").get()))

    tasks.withType(KotlinCompile::class.java).configureEach {
      it.compilerOptions.allWarningsAsErrors.set(ci)
    }
  }

  private fun Project.configureKtfmt() {
    pluginManager.apply(Plugins.KTFMT)
    extensions.configure(KtfmtExtension::class.java) { it.googleStyle() }
  }

  private fun Project.configureDependencySubstitution() {
    val group = providers.gradleProperty("GROUP").get()
    configurations.configureEach {
      it.resolutionStrategy.dependencySubstitution { substitution ->
        substitution
          .substitute(substitution.module("$group:compiler"))
          .using(substitution.project(":compiler"))
      }
    }
  }

  private fun Project.configureTestTask() {
    tasks.withType(Test::class.java).configureEach { testTask ->
      if (ci) {
        testTask.testLogging {
          it.showExceptions = true
          it.showCauses = true
          it.showStackTraces = true
          it.showStandardStreams = true
        }
      }

      // Otherwise the java icon keeps popping up in the system tray while running tests.
      testTask.systemProperty("java.awt.headless", "true")
    }
  }
}
