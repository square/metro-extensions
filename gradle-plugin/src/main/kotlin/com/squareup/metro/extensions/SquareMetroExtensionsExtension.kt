package com.squareup.metro.extensions

import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
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
 *
 *   // Disable the DevelopmentAppComponent FIR extension.
 *   enableDevelopmentAppComponent.set(false)
 * }
 * ```
 *
 * By default, a compilation is considered a release build if its name ends with "release"
 * (case-insensitive), matching Android build variant naming conventions.
 */
public abstract class SquareMetroExtensionsExtension @Inject constructor(objects: ObjectFactory) {

  internal var isReleaseBuildPredicate: ((KotlinCompilation<*>) -> Boolean)? = null

  /**
   * Whether the `@DevelopmentAppComponent` FIR extension is enabled. When `false`, the extension
   * will not generate `MetroComponent` and `MetroComponent.Factory` interfaces for classes
   * annotated with `@DevelopmentAppComponent`. Defaults to `true`.
   */
  public val enableDevelopmentAppComponent: Property<Boolean> =
    objects.property(Boolean::class.java).convention(true)

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
