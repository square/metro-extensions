package com.squareup.metro.extensions.robot

import com.fueledbycaffeine.autoservice.AutoService
import com.squareup.metro.extensions.ArgNames
import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.Keys.ContributesRobotGeneratorKey
import com.squareup.metro.extensions.fir.buildAnnotationWithScope
import com.squareup.metro.extensions.fir.buildFirFunction
import com.squareup.metro.extensions.fir.extractScopeArgument
import com.squareup.metro.extensions.fir.extractScopeClassId
import com.squareup.metro.extensions.fir.hasAnnotation
import dev.zacsweers.metro.compiler.MetroOptions
import dev.zacsweers.metro.compiler.api.fir.MetroFirDeclarationGenerationExtension
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClass
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.kotlinScopeProvider
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.toEffectiveVisibility
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

/**
 * Generates a nested `RobotContribution` interface for classes annotated with `@ContributesRobot`.
 *
 * For a class like:
 * ```
 * @Inject
 * @ContributesRobot(SomeScope::class)
 * class AbcRobot : ScreenRobot<AbcRobot>()
 * ```
 *
 * This generator produces:
 * ```
 * @ContributesTo(SomeScope::class)
 * interface RobotContribution {
 *   fun getAbcRobot(): AbcRobot
 * }
 * ```
 *
 * The accessor function is registered through [getCallableNamesForClass] and [generateFunctions]
 * (rather than added directly to declarations) so that the Kotlin compiler properly serializes it
 * into the class metadata. This is required for cross-module compilation where Metro reads the
 * metadata to discover abstract members that need implementations in the graph.
 */
public class ContributesRobotFir(session: FirSession) :
  MetroFirDeclarationGenerationExtension(session) {

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(ContributesRobotIds.PREDICATE)
  }

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext,
  ): Set<Name> {
    if (hasAnnotation(classSymbol, ContributesRobotIds.CONTRIBUTES_ROBOT_CLASS_ID, session)) {
      return setOf(ContributesRobotIds.NESTED_INTERFACE_NAME)
    }
    return emptySet()
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): FirClassLikeSymbol<*>? {
    if (name != ContributesRobotIds.NESTED_INTERFACE_NAME) return null
    if (!hasAnnotation(owner, ContributesRobotIds.CONTRIBUTES_ROBOT_CLASS_ID, session)) return null
    val scopeArg =
      extractScopeArgument(owner, ContributesRobotIds.CONTRIBUTES_ROBOT_CLASS_ID, session)
        ?: return null

    val nestedClassId = owner.classId.createNestedClassId(name)
    val classSymbol = FirRegularClassSymbol(nestedClassId)

    val klass = buildRegularClass {
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = ContributesRobotGeneratorKey.origin
      source = owner.source
      classKind = ClassKind.INTERFACE
      scopeProvider = session.kotlinScopeProvider
      this.name = nestedClassId.shortClassName
      symbol = classSymbol
      status =
        FirResolvedDeclarationStatusImpl(
          Visibilities.Public,
          Modality.ABSTRACT,
          Visibilities.Public.toEffectiveVisibility(owner, forClass = true),
        )
      superTypeRefs += session.builtinTypes.anyType
      annotations += buildAnnotationWithScope(ClassIds.CONTRIBUTES_TO, ArgNames.SCOPE, scopeArg)
    }

    return klass.symbol
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    if (!isGeneratedContributionInterface(classSymbol)) return emptySet()

    // The outer class is the @ContributesRobot-annotated class that owns this nested interface.
    val outerClassId = classSymbol.classId.outerClassId ?: return emptySet()
    val functionName = "get${outerClassId.shortClassName.identifier}"
    return setOf(Name.identifier(functionName))
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    val owner = context?.owner ?: return emptyList()
    if (!isGeneratedContributionInterface(owner)) return emptyList()

    val outerClassId = owner.classId.outerClassId ?: return emptyList()
    val outerSymbol =
      session.symbolProvider.getClassLikeSymbolByClassId(outerClassId) as? FirRegularClassSymbol
        ?: return emptyList()

    val outerClassType = outerSymbol.defaultType()
    val dispatchType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(owner.classId),
        emptyArray(),
        isMarkedNullable = false,
      )

    val functionSymbol = FirNamedFunctionSymbol(callableId)

    buildFirFunction {
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = ContributesRobotGeneratorKey.origin
      symbol = functionSymbol
      name = callableId.callableName
      returnTypeRef = outerClassType.toFirResolvedTypeRef()
      dispatchReceiverType = dispatchType
      status =
        FirResolvedDeclarationStatusImpl(
          Visibilities.Public,
          Modality.ABSTRACT,
          Visibilities.Public.toEffectiveVisibility(outerSymbol, forClass = true),
        )
    }

    return listOf(functionSymbol)
  }

  override fun getContributionHints(): List<ContributionHint> {
    return session.predicateBasedProvider
      .getSymbolsByPredicate(ContributesRobotIds.PREDICATE)
      .filterIsInstance<FirRegularClassSymbol>()
      .mapNotNull { classSymbol ->
        val scopeClassId =
          extractScopeClassId(
            classSymbol,
            ContributesRobotIds.CONTRIBUTES_ROBOT_CLASS_ID,
            session,
          ) ?: return@mapNotNull null
        val nestedInterfaceClassId =
          classSymbol.classId.createNestedClassId(ContributesRobotIds.NESTED_INTERFACE_NAME)
        ContributionHint(
          contributingClassId = nestedInterfaceClassId,
          scope = scopeClassId,
        )
      }
  }

  private fun isGeneratedContributionInterface(classSymbol: FirClassSymbol<*>): Boolean {
    return classSymbol.origin == ContributesRobotGeneratorKey.origin &&
      classSymbol.name == ContributesRobotIds.NESTED_INTERFACE_NAME
  }

  @AutoService(MetroFirDeclarationGenerationExtension.Factory::class)
  public class Factory : MetroFirDeclarationGenerationExtension.Factory {
    override fun create(
      session: FirSession,
      options: MetroOptions,
    ): MetroFirDeclarationGenerationExtension = ContributesRobotFir(session)
  }
}
