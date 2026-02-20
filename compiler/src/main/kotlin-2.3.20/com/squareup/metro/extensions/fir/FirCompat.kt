package com.squareup.metro.extensions.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.builder.FirNamedFunctionBuilder
import org.jetbrains.kotlin.fir.declarations.builder.buildNamedFunction
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.plugin.SimpleFunctionBuildingContext
import org.jetbrains.kotlin.fir.plugin.createTopLevelFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.CallableId

internal inline fun buildFirFunction(
  init: FirNamedFunctionBuilder.() -> Unit,
): FirNamedFunction = buildNamedFunction {
  isLocal = false
  init()
}

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
internal fun FirExtension.createHintFunction(
  key: GeneratedDeclarationKey,
  callableId: CallableId,
  returnType: ConeKotlinType,
  containingFileName: String,
  config: SimpleFunctionBuildingContext.() -> Unit,
): FirNamedFunctionSymbol {
  return createTopLevelFunction(key, callableId, returnType, containingFileName, config)
    .symbol as FirNamedFunctionSymbol
}
