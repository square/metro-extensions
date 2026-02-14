package com.squareup.metro.extensions

import com.squareup.metro.extensions.fir.SquareMetroExtensionsFirCheckers
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

public class SquareMetroExtensionsPluginRegistrar : FirExtensionRegistrar() {
  override fun ExtensionRegistrarContext.configurePlugin() {
    +::SquareMetroExtensionsFirCheckers
  }
}
