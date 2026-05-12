package com.squareup.metro.extensions.featureflag

import com.fueledbycaffeine.autoservice.AutoService
import dev.zacsweers.metro.compiler.MetroOptions
import dev.zacsweers.metro.compiler.api.fir.MetroContributionExtension
import dev.zacsweers.metro.compiler.compat.CompatContext
import dev.zacsweers.metro.compiler.fir.MetroFirTypeResolver
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.name.ClassId

/**
 * Registers the feature flag predicates with Metro's contribution pipeline.
 *
 * Generated feature flag contributions are `@BindingContainer` objects. They are discovered through
 * [ContributesFeatureFlagFir.getContributionHints], not through synthetic `MetroContribution`
 * supertypes, so this extension does not need to return contributions directly.
 */
@Suppress("UNUSED_PARAMETER")
public class ContributesFeatureFlagMetroExtension(session: FirSession) :
  MetroContributionExtension {

  private val predicate = ContributesFeatureFlagIds.PREDICATE

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
      return ContributesFeatureFlagMetroExtension(session)
    }
  }
}
