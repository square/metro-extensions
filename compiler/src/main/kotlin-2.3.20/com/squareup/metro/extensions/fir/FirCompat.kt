package com.squareup.metro.extensions.fir

import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.builder.FirNamedFunctionBuilder
import org.jetbrains.kotlin.fir.declarations.builder.buildNamedFunction

internal inline fun buildFirFunction(
  init: FirNamedFunctionBuilder.() -> Unit,
): FirNamedFunction = buildNamedFunction {
  isLocal = false
  init()
}
