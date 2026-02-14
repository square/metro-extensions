package com.squareup.metro.extensions

import com.fueledbycaffeine.autoservice.AutoService
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@AutoService(CompilerPluginRegistrar::class)
public class SquareMetroExtensionsPluginComponentRegistrar : CompilerPluginRegistrar() {
  override val pluginId: String = "com.squareup.metro.extensions"
  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    FirExtensionRegistrarAdapter.registerExtension(SquareMetroExtensionsPluginRegistrar())
  }
}
