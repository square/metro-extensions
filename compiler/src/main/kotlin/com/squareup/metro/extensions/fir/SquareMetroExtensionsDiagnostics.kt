package com.squareup.metro.extensions.fir

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.psi.KtElement

internal object SquareMetroExtensionsDiagnostics : KtDiagnosticsContainer() {

  val CONTRIBUTES_MULTIBINDING_SCOPED_ERROR by
    error1<KtElement, String>(SourceElementPositioningStrategies.NAME_IDENTIFIER)

  override fun getRendererFactory(): BaseDiagnosticRendererFactory {
    return SquareMetroExtensionsErrorMessages
  }
}

private object SquareMetroExtensionsErrorMessages : BaseDiagnosticRendererFactory() {
  override val MAP by
    KtDiagnosticFactoryToRendererMap("SquareMetroExtensions") { map ->
      map.apply {
        put(
          SquareMetroExtensionsDiagnostics.CONTRIBUTES_MULTIBINDING_SCOPED_ERROR,
          "{0}",
          CommonRenderers.STRING,
        )
      }
    }
}
