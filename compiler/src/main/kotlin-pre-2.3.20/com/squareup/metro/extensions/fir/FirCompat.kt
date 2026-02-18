package com.squareup.metro.extensions.fir

import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.FirSimpleFunctionBuilder
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.expressions.FirArrayLiteral
import org.jetbrains.kotlin.fir.expressions.builder.FirArrayLiteralBuilder
import org.jetbrains.kotlin.fir.expressions.builder.buildArrayLiteral

internal inline fun buildFirFunction(init: FirSimpleFunctionBuilder.() -> Unit): FirSimpleFunction =
  buildSimpleFunction(init)

internal inline fun buildFirArrayLiteral(init: FirArrayLiteralBuilder.() -> Unit): FirArrayLiteral =
  buildArrayLiteral(init)
