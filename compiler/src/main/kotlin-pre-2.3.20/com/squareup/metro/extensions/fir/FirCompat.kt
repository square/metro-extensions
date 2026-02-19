package com.squareup.metro.extensions.fir

import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.FirSimpleFunctionBuilder
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction

internal inline fun buildFirFunction(init: FirSimpleFunctionBuilder.() -> Unit): FirSimpleFunction =
  buildSimpleFunction(init)
