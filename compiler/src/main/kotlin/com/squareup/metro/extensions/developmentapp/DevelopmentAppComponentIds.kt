package com.squareup.metro.extensions.developmentapp

import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object DevelopmentAppComponentIds {
  private val DEVELOPMENT_APP_COMPONENT_FQ_NAME =
    FqName("com.squareup.development.shell.DevelopmentAppComponent")

  val METRO_COMPONENT_NAME = Name.identifier("MetroComponent")

  val FACTORY_NAME = Name.identifier("Factory")

  val PREDICATE = LookupPredicate.create { annotated(DEVELOPMENT_APP_COMPONENT_FQ_NAME) }
}
