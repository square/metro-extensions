package com.squareup.metro.extensions.fir

import com.squareup.metro.extensions.robot.ContributesRobotChecker
import com.squareup.metro.extensions.scoped.ContributesMultibindingScopedChecker
import com.squareup.metro.extensions.scoped.ContributesMultibindingScopedReplacesChecker
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

internal class SquareMetroExtensionsFirCheckers(session: FirSession) :
  FirAdditionalCheckersExtension(session) {
  override val declarationCheckers: DeclarationCheckers =
    object : DeclarationCheckers() {
      override val classCheckers: Set<FirClassChecker> =
        setOf(
          ContributesMultibindingScopedChecker,
          ContributesMultibindingScopedReplacesChecker,
          ContributesRobotChecker,
        )
    }
}
