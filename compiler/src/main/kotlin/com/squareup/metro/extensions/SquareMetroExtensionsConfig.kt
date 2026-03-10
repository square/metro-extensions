package com.squareup.metro.extensions

import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent

/**
 * Session component that holds configuration for the metro-extensions compiler plugin.
 *
 * Registered as a [FirExtensionSessionComponent] so that SPI-loaded Metro extensions (e.g.,
 * [ContributesServiceFir][service.ContributesServiceFir]) can access it via
 * `session.squareMetroExtensionsConfig`.
 */
internal class SquareMetroExtensionsConfig(session: FirSession, val isReleaseBuild: Boolean) :
  FirExtensionSessionComponent(session) {

  companion object {
    val IS_RELEASE_BUILD_KEY = CompilerConfigurationKey.create<Boolean>("is release build")

    fun getFactory(isReleaseBuild: Boolean): Factory {
      return Factory { session -> SquareMetroExtensionsConfig(session, isReleaseBuild) }
    }
  }
}

internal val FirSession.squareMetroExtensionsConfig: SquareMetroExtensionsConfig by
  FirSession.sessionComponentAccessor()
