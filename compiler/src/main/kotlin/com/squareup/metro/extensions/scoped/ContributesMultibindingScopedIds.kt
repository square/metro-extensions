package com.squareup.metro.extensions.scoped

import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Shared identifiers for the `@ContributesMultibindingScoped` compiler plugin support.
 *
 * Used by both:
 * - [ContributesMultibindingScopedFir] (FIR generator that creates the contribution interface)
 * - [ContributesMultibindingScopedMetroExtension] (Metro extension that bridges predicate gap)
 */
internal object ContributesMultibindingScopedIds {

  val CONTRIBUTES_MULTIBINDING_SCOPED_CLASS_ID =
    ClassId(FqName("com.squareup.dagger"), Name.identifier("ContributesMultibindingScoped"))

  val CONTRIBUTES_MULTIBINDING_SCOPED_FQ_NAME =
    FqName("com.squareup.dagger.ContributesMultibindingScoped")

  val NESTED_INTERFACE_NAME = Name.identifier("MultibindingScopedContribution")

  /** Predicate matching classes annotated with `@ContributesMultibindingScoped`. */
  val PREDICATE = LookupPredicate.create { annotated(CONTRIBUTES_MULTIBINDING_SCOPED_FQ_NAME) }
}
