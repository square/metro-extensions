package com.squareup.metro.extensions.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataKey
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataRegistry
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.serialization.providedDeclarationsForMetadataService
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol

/**
 * Registers predeclared functions with Kotlin's supported metadata service.
 *
 * Metro needs these functions in `declarationSymbols` before it discovers nested provider factories
 * and binding mirrors. Kotlin, however, serializes generated classes through their member scopes,
 * which only contain functions returned by `generateFunctions`. Providing the same functions
 * through both paths would make FIR2IR convert them twice.
 *
 * Call from `getCallableNamesForClass`, once the owner is bound and available in the symbol
 * provider. Keep these functions out of `generateFunctions`; the metadata service serializes the
 * original, fully resolved FIR declarations, including parameter annotations and annotation
 * arguments.
 */
@OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
internal fun FirSession.registerPredeclaredFunctionMetadata(
  owner: FirClassSymbol<*>,
  key: GeneratedDeclarationKey,
) {
  if (owner.origin != key.origin) return
  for (symbol in owner.declarationSymbols.filterIsInstance<FirNamedFunctionSymbol>()) {
    val function = symbol.fir
    if (function.origin != key.origin || function.isRegisteredForMetadata == true) continue
    providedDeclarationsForMetadataService.registerDeclaration(function)
    function.isRegisteredForMetadata = true
  }
}

private object RegisteredForMetadata : FirDeclarationDataKey()

private var FirNamedFunction.isRegisteredForMetadata: Boolean? by
  FirDeclarationDataRegistry.data(RegisteredForMetadata)
