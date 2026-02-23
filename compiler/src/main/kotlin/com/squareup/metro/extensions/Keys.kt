package com.squareup.metro.extensions

import org.jetbrains.kotlin.GeneratedDeclarationKey

internal object Keys {
  data object ContributesMultibindingScopedGeneratorKey : GeneratedDeclarationKey() {
    override fun toString(): String = "ContributesMultibindingScopedGenerator"
  }

  data object ContributesRobotGeneratorKey : GeneratedDeclarationKey() {
    override fun toString(): String = "ContributesRobotGenerator"
  }

  data object ContributesServiceGeneratorKey : GeneratedDeclarationKey() {
    override fun toString(): String = "ContributesServiceGenerator"
  }

  data object ContributesFeatureFlagGeneratorKey : GeneratedDeclarationKey() {
    override fun toString(): String = "ContributesFeatureFlagGenerator"
  }
}
