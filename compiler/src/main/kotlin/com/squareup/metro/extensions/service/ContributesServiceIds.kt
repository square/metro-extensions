package com.squareup.metro.extensions.service

import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Shared identifiers for the `@ContributesService` compiler plugin support.
 *
 * Used by:
 * - [ContributesServiceFir] (FIR generator that creates the ServiceContribution container)
 * - [ContributesServiceMetroExtension] (Metro extension that registers the service predicate)
 * - [ContributesServiceChecker] (FIR checker for validation)
 */
internal object ContributesServiceIds {

  val CONTRIBUTES_SERVICE_CLASS_ID =
    ClassId(FqName("com.squareup.services.anvil"), Name.identifier("ContributesService"))

  val CONTRIBUTES_SERVICE_FQ_NAME = FqName("com.squareup.services.anvil.ContributesService")

  val NESTED_CONTAINER_NAME = Name.identifier("ServiceContribution")

  /** Predicate matching classes annotated with `@ContributesService`. */
  val PREDICATE = LookupPredicate.create { annotated(CONTRIBUTES_SERVICE_FQ_NAME) }
}
