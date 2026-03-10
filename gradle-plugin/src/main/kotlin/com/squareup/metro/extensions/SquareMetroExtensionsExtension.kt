package com.squareup.metro.extensions

import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

/**
 * Configuration extension for the metro-extensions Gradle plugin.
 *
 * ```kotlin
 * metroExtensions {
 *   // Override the default release build detection with a custom predicate.
 *   isReleaseBuild { compilation ->
 *     compilation.name.endsWith("release", ignoreCase = true)
 *   }
 * }
 * ```
 *
 * By default, a compilation is considered a release build if its name ends with "release"
 * (case-insensitive), matching Android build variant naming conventions.
 */
public open class SquareMetroExtensionsExtension {

  internal var isReleaseBuildPredicate: ((KotlinCompilation<*>) -> Boolean)? = null

  /**
   * Sets a predicate that determines whether a given [KotlinCompilation] is a release build.
   *
   * In release builds, the `@FakeMode isFakeMode` parameter is omitted from generated
   * `@ContributesService` provider functions, since `@FakeMode` is not available in release builds.
   */
  public fun isReleaseBuild(predicate: (KotlinCompilation<*>) -> Boolean) {
    isReleaseBuildPredicate = predicate
  }
}
