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

  val HOLDER_CLASS_SUFFIX = "MultibindingScopedContributions"
  val CONTRIBUTION_CLASS_SUFFIX = "MultibindingScopedContribution"

  val NESTED_INTERFACE_NAME = Name.identifier(CONTRIBUTION_CLASS_SUFFIX)

  /** Predicate matching classes annotated with `@ContributesMultibindingScoped`. */
  val PREDICATE = LookupPredicate.create { annotated(CONTRIBUTES_MULTIBINDING_SCOPED_FQ_NAME) }

  /** Predicate matching Metro contributions that can replace scoped contributions. */
  val REPLACING_CONTRIBUTION_PREDICATE = LookupPredicate.create {
    annotated(FqName("dev.zacsweers.metro.ContributesTo")) or
      annotated(FqName("dev.zacsweers.metro.ContributesBinding")) or
      annotated(FqName("dev.zacsweers.metro.ContributesIntoSet")) or
      annotated(FqName("dev.zacsweers.metro.ContributesIntoMap"))
  }

  fun holderClassId(contributedClassId: ClassId): ClassId {
    val contributedName =
      contributedClassId.relativeClassName.pathSegments().joinToString(separator = "") {
        it.asString()
      }
    return ClassId(
      contributedClassId.packageFqName,
      Name.identifier("$contributedName$HOLDER_CLASS_SUFFIX"),
    )
  }

  fun contributionClassId(contributedClassId: ClassId): ClassId {
    return holderClassId(contributedClassId).createNestedClassId(NESTED_INTERFACE_NAME)
  }

  fun legacyScopedProvidersClassId(contributedClassId: ClassId): ClassId {
    val contributedPackage = contributedClassId.packageFqName.asString()
    val packageName =
      if (contributedPackage.isEmpty()) {
        "anvil.register.scoped"
      } else {
        "anvil.register.scoped.$contributedPackage"
      }
    return ClassId(
      FqName(packageName),
      Name.identifier("${contributedClassId.shortClassName.asString()}ScopedProviders"),
    )
  }
}
