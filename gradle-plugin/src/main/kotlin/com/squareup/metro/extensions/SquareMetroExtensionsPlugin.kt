package com.squareup.metro.extensions

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@Suppress("unused")
public class SquareMetroExtensionsPlugin : KotlinCompilerPluginSupportPlugin {

  override fun apply(target: Project) {
    target.extensions.create("metroExtensions", SquareMetroExtensionsExtension::class.java)
  }

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

  override fun getCompilerPluginId(): String = "com.squareup.metro.extensions"

  override fun getPluginArtifact(): SubpluginArtifact =
    SubpluginArtifact(groupId = GROUP, artifactId = "compiler", version = VERSION)

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>
  ): Provider<List<SubpluginOption>> {
    val project = kotlinCompilation.target.project
    val extension = project.extensions.getByType(SquareMetroExtensionsExtension::class.java)

    return project.provider {
      val predicate = extension.isReleaseBuildPredicate ?: isReleaseBuildPredicateDefault
      listOf(
        SubpluginOption(key = "isReleaseBuild", value = predicate(kotlinCompilation).toString()),
        SubpluginOption(
          key = "enableDevelopmentAppComponent",
          value = extension.enableDevelopmentAppComponent.get().toString(),
        ),
      )
    }
  }

  private companion object {
    /**
     * Default predicate for detecting release builds. A compilation is considered a release build
     * if its name ends with "release" (case-insensitive), matching Android build variant naming
     * conventions (e.g., "release", "fullRelease"). For non-Android (JVM) projects this returns
     * `false`.
     */
    val isReleaseBuildPredicateDefault = { compilation: KotlinCompilation<*> ->
      compilation.name.endsWith("release", ignoreCase = true)
    }
  }
}
