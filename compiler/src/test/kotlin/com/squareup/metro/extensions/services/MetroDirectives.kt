package com.squareup.metro.extensions.services

import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

object MetroDirectives : SimpleDirectivesContainer() {
  val GENERATE_CONTRIBUTION_HINTS_IN_FIR by
    directive("Enable generation of contribution hints in FIR instead of IR.")

  val IS_RELEASE_BUILD by directive("Treat compilation as a release build.")

  val DISABLE_DEVELOPMENT_APP_COMPONENT by
    directive("Disable the @DevelopmentAppComponent FIR extension.")
}
