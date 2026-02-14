package com.squareup.metro.extensions

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@Suppress("unused")
public class SquareMetroExtensionsPlugin : KotlinCompilerPluginSupportPlugin {
  override fun apply(target: Project): Unit = Unit

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

  override fun getCompilerPluginId(): String = "com.squareup.metro.extensions"

  override fun getPluginArtifact(): SubpluginArtifact =
    SubpluginArtifact(groupId = GROUP, artifactId = "compiler", version = VERSION)

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>
  ): Provider<List<SubpluginOption>> {
    val project = kotlinCompilation.target.project
    return project.provider { emptyList() }
  }
}
