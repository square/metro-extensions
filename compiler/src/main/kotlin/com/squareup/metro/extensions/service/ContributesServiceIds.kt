package com.squareup.metro.extensions.service

import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Shared identifiers for the `@ContributesService` compiler plugin support.
 *
 * Used by:
 * - [ContributesServiceFir] (FIR generator that creates the ServiceContribution interface)
 * - [ContributesServiceMetroExtension] (Metro extension that bridges predicate gap)
 * - [ContributesServiceChecker] (FIR checker for validation)
 */
internal object ContributesServiceIds {

  val CONTRIBUTES_SERVICE_CLASS_ID =
    ClassId(FqName("com.squareup.services.anvil"), Name.identifier("ContributesService"))

  val CONTRIBUTES_SERVICE_FQ_NAME = FqName("com.squareup.services.anvil.ContributesService")

  val NESTED_INTERFACE_NAME = Name.identifier("ServiceContribution")

  /** Predicate matching classes annotated with `@ContributesService`. */
  val PREDICATE = LookupPredicate.create { annotated(CONTRIBUTES_SERVICE_FQ_NAME) }
}
