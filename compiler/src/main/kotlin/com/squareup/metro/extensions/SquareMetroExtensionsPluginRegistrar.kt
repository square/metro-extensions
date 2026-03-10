package com.squareup.metro.extensions

import com.squareup.metro.extensions.fir.SquareMetroExtensionsFirCheckers
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

public class SquareMetroExtensionsPluginRegistrar(
  private val isReleaseBuild: Boolean,
  private val enableDevelopmentAppComponent: Boolean,
) : FirExtensionRegistrar() {
  override fun ExtensionRegistrarContext.configurePlugin() {
    +SquareMetroExtensionsConfig.getFactory(isReleaseBuild, enableDevelopmentAppComponent)
    +::SquareMetroExtensionsFirCheckers
  }
}
