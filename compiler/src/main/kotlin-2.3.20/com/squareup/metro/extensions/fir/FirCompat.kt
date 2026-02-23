package com.squareup.metro.extensions.fir

import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.builder.FirNamedFunctionBuilder
import org.jetbrains.kotlin.fir.declarations.builder.buildNamedFunction
import org.jetbrains.kotlin.fir.expressions.FirCollectionLiteral
import org.jetbrains.kotlin.fir.expressions.builder.FirCollectionLiteralBuilder
import org.jetbrains.kotlin.fir.expressions.builder.buildCollectionLiteral

internal inline fun buildFirFunction(
  init: FirNamedFunctionBuilder.() -> Unit,
): FirNamedFunction = buildNamedFunction {
  isLocal = false
  init()
}

internal inline fun buildFirArrayLiteral(
  init: FirCollectionLiteralBuilder.() -> Unit,
): FirCollectionLiteral = buildCollectionLiteral(init)
