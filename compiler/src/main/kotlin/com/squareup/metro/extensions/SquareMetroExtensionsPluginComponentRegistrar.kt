@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.squareup.metro.extensions

import com.fueledbycaffeine.autoservice.AutoService
import dev.zacsweers.metro.compiler.MetroOption
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@AutoService(CompilerPluginRegistrar::class)
public class SquareMetroExtensionsPluginComponentRegistrar : CompilerPluginRegistrar() {
  override val pluginId: String = "com.squareup.metro.extensions"
  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    @Suppress("UNCHECKED_CAST")
    val key =
      MetroOption.GENERATE_CONTRIBUTION_HINTS_IN_FIR.raw.key
        as org.jetbrains.kotlin.config.CompilerConfigurationKey<Boolean>
    val generateHintsInFir = configuration.get(key, false)
    FirExtensionRegistrarAdapter.registerExtension(
      SquareMetroExtensionsPluginRegistrar(generateHintsInFir)
    )
  }
}
