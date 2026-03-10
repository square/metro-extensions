package com.squareup.metro.extensions

import com.fueledbycaffeine.autoservice.AutoService
import com.squareup.metro.extensions.featureflag.ContributesFeatureFlagIrExtension
import com.squareup.metro.extensions.service.ContributesServiceIrExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@AutoService(CompilerPluginRegistrar::class)
public class SquareMetroExtensionsPluginComponentRegistrar : CompilerPluginRegistrar() {
  override val pluginId: String = "com.squareup.metro.extensions"
  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val isReleaseBuild = configuration.get(SquareMetroExtensionsConfig.IS_RELEASE_BUILD_KEY, false)
    val enableDevelopmentAppComponent =
      configuration.get(SquareMetroExtensionsConfig.ENABLE_DEVELOPMENT_APP_COMPONENT_KEY, true)
    FirExtensionRegistrarAdapter.registerExtension(
      SquareMetroExtensionsPluginRegistrar(isReleaseBuild, enableDevelopmentAppComponent)
    )
    IrGenerationExtension.registerExtension(ContributesServiceIrExtension())
    IrGenerationExtension.registerExtension(ContributesFeatureFlagIrExtension())
  }
}
