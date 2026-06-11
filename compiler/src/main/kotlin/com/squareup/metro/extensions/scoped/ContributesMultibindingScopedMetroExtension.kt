package com.squareup.metro.extensions.scoped

import com.fueledbycaffeine.autoservice.AutoService
import dev.zacsweers.metro.compiler.MetroOptions
import dev.zacsweers.metro.compiler.api.fir.MetroContributionExtension
import dev.zacsweers.metro.compiler.compat.CompatContext
import dev.zacsweers.metro.compiler.fir.MetroFirTypeResolver
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.name.ClassId

/**
 * Registers the scoped multibinding predicate with Metro's contribution pipeline.
 *
 * Generated scoped multibinding contributions are binding containers. They are discovered through
 * [ContributesMultibindingScopedFir.getContributionHints], not through synthetic
 * `MetroContribution` supertypes, so this extension does not need to return contributions directly.
 */
@Suppress("UNUSED_PARAMETER")
public class ContributesMultibindingScopedMetroExtension(session: FirSession) :
  MetroContributionExtension {

  private val predicate = ContributesMultibindingScopedIds.PREDICATE

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(predicate)
  }

  override fun getContributions(
    scopeClassId: ClassId,
    typeResolverFactory: MetroFirTypeResolver.Factory,
  ): List<MetroContributionExtension.Contribution> {
    return emptyList()
  }

  @AutoService(MetroContributionExtension.Factory::class)
  public class Factory : MetroContributionExtension.Factory {
    override fun create(
      session: FirSession,
      options: MetroOptions,
      compatContext: CompatContext,
    ): MetroContributionExtension {
      return ContributesMultibindingScopedMetroExtension(session)
    }
  }
}
