package com.squareup.metro.extensions

import com.squareup.metro.extensions.fir.SquareMetroExtensionsFirCheckers
import com.squareup.metro.extensions.fir.metro.ContributionHintFirGenerator
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

public class SquareMetroExtensionsPluginRegistrar(
  private val generateContributionHintsInFir: Boolean
) : FirExtensionRegistrar() {
  override fun ExtensionRegistrarContext.configurePlugin() {
    +::SquareMetroExtensionsFirCheckers
    if (generateContributionHintsInFir) {
      +::ContributionHintFirGenerator
    }
  }
}
