package com.squareup.metro.extensions.scoped

import com.fueledbycaffeine.autoservice.AutoService
import com.squareup.metro.extensions.ArgNames
import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.Keys.ContributesMultibindingScopedGeneratorKey
import com.squareup.metro.extensions.fir.buildAnnotationCallWithScope
import com.squareup.metro.extensions.fir.buildClassExpression
import com.squareup.metro.extensions.fir.extractScopeArgument
import com.squareup.metro.extensions.fir.extractScopeClassId
import com.squareup.metro.extensions.fir.hasAnnotation
import dev.zacsweers.metro.compiler.MetroOptions
import dev.zacsweers.metro.compiler.api.fir.MetroFirDeclarationGenerationExtension
import dev.zacsweers.metro.compiler.compat.CompatContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildNamedFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClass
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirAnnotationResolvePhase
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCall
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.plugin.createDefaultPrivateConstructor
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.SupertypeSupplier
import org.jetbrains.kotlin.fir.resolve.TypeResolutionConfiguration
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.typeResolver
import org.jetbrains.kotlin.fir.scopes.createImportingScopes
import org.jetbrains.kotlin.fir.scopes.kotlinScopeProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.toEffectiveVisibility
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

private const val SCOPED_PROVIDER_FUNCTION_NAME = "provideContributedMultibindingScoped"

/**
 * Generates a nested `MultibindingScopedContribution` interface for classes annotated with
 * `@ContributesMultibindingScoped`.
 *
 * For a class like:
 * ```
 * @ContributesMultibindingScoped(SomeScope::class)
 * class MyService(private val dependency: Dependency) : Scoped
 * ```
 *
 * This generator produces:
 * ```
 * @ContributesTo(SomeScope::class)
 * @BindingContainer
 * interface MultibindingScopedContribution {
 *   companion object {
 *     @Provides
 *     fun provideContributedMultibindingScoped(dependency: Dependency): MyService
 *   }
 *
 *   @Binds @IntoSet @ForScope(SomeScope::class)
 *   fun bindsMyService(myService: MyService): Scoped
 * }
 * ```
 *
 * Implements [MetroFirDeclarationGenerationExtension] so that Metro's
 * [CompositeMetroFirDeclarationGenerationExtension][dev.zacsweers.metro.compiler.fir.generators.CompositeMetroFirDeclarationGenerationExtension]
 * automatically delegates callbacks from Metro's native generators (e.g.,
 * `ContributionsFirGenerator`, `InjectedClassFirGenerator`) to process the generated
 * `MultibindingScopedContribution` interface. This eliminates the need to manually discover and
 * call other generators.
 *
 * The generated interface and its `@Binds` function use the plugin's own
 * [GeneratedDeclarationKey][org.jetbrains.kotlin.GeneratedDeclarationKey] origin. Metro's composite
 * handles the routing: when Metro's native generators return names for our generated class, the
 * composite tracks ownership so the correct generator is called for each declaration.
 *
 * The function is added directly to the class's declarations list (rather than through
 * `getCallableNamesForClass`/`generateFunctions`) so Metro can see it when deciding what nested
 * classes to generate.
 */
public class ContributesMultibindingScopedFir(session: FirSession) :
  MetroFirDeclarationGenerationExtension(session) {

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(ContributesMultibindingScopedIds.PREDICATE)
  }

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext,
  ): Set<Name> {
    if (needsGeneratedProvidesCompanion(classSymbol)) {
      return setOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
    }
    if (
      hasAnnotation(
        classSymbol,
        ContributesMultibindingScopedIds.CONTRIBUTES_MULTIBINDING_SCOPED_CLASS_ID,
        session,
      )
    ) {
      return setOf(ContributesMultibindingScopedIds.NESTED_INTERFACE_NAME)
    }
    return emptySet()
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): FirClassLikeSymbol<*>? {
    if (name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) {
      val scopedSymbol = scopedSymbolForGeneratedContribution(owner) ?: return null
      if (!needsProvidesFunction(scopedSymbol)) return null
      val companionClassId = owner.classId.createNestedClassId(name)
      val providesFunction = buildProvidesFunction(companionClassId, scopedSymbol) ?: return null
      return buildProvidesCompanionObject(companionClassId, owner, providesFunction)
    }

    if (name != ContributesMultibindingScopedIds.NESTED_INTERFACE_NAME) return null
    if (
      !hasAnnotation(
        owner,
        ContributesMultibindingScopedIds.CONTRIBUTES_MULTIBINDING_SCOPED_CLASS_ID,
        session,
      )
    )
      return null
    val scopeArg =
      extractScopeArgument(
        owner,
        ContributesMultibindingScopedIds.CONTRIBUTES_MULTIBINDING_SCOPED_CLASS_ID,
        session,
      ) ?: return null

    val nestedClassId = owner.classId.createNestedClassId(name)
    val classSymbol = FirRegularClassSymbol(nestedClassId)

    // Build the @Binds function and add it directly to the class declarations.
    // This makes it visible to Metro's getNestedClassifiersNames (which checks for @Binds
    // functions to decide whether to generate BindsMirror).
    val bindsFunction = buildBindsFunction(nestedClassId, owner, scopeArg)

    val klass = buildRegularClass {
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = ContributesMultibindingScopedGeneratorKey.origin
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
      annotations +=
        buildAnnotationCallWithScope(
          ClassIds.CONTRIBUTES_TO,
          ArgNames.SCOPE,
          scopeArg,
          owner,
          session,
        )
      annotations += buildSimpleAnnotationCall(ClassIds.BINDING_CONTAINER, classSymbol)
      // @Origin(OwnerClass::class) so Metro can trace this contribution back to the
      // outer class for replaces/excludes in multi-compilation scenarios.
      annotations +=
        buildAnnotationCallWithScope(
          ClassIds.ORIGIN,
          ArgNames.VALUE,
          buildClassExpression(owner, session),
          classSymbol,
          session,
        )
      // Add the function directly to the class declarations
      declarations += bindsFunction
    }

    return klass.symbol
  }

  override fun getContributionHints(): List<ContributionHint> {
    return session.predicateBasedProvider
      .getSymbolsByPredicate(ContributesMultibindingScopedIds.PREDICATE)
      .filterIsInstance<FirRegularClassSymbol>()
      .mapNotNull { classSymbol ->
        val scopeClassId =
          extractScopeClassId(
            classSymbol,
            ContributesMultibindingScopedIds.CONTRIBUTES_MULTIBINDING_SCOPED_CLASS_ID,
            session,
          ) ?: return@mapNotNull null
        val nestedInterfaceClassId =
          classSymbol.classId.createNestedClassId(
            ContributesMultibindingScopedIds.NESTED_INTERFACE_NAME
          )
        ContributionHint(contributingClassId = nestedInterfaceClassId, scope = scopeClassId)
      }
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    return if (isGeneratedProvidesCompanion(classSymbol)) {
      setOf(SpecialNames.INIT)
    } else {
      emptySet()
    }
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
    return if (isGeneratedProvidesCompanion(context.owner)) {
      listOf(
        createDefaultPrivateConstructor(context.owner, ContributesMultibindingScopedGeneratorKey)
          .symbol
      )
    } else {
      emptyList()
    }
  }

  private fun buildBindsFunction(
    classId: ClassId,
    outerOwner: FirClassSymbol<*>,
    scopeArg: FirExpression,
  ): FirDeclaration {
    val outerClassId = outerOwner.classId
    val functionName = "binds${outerClassId.shortClassName.identifier}"
    val callableId = CallableId(classId, Name.identifier(functionName))

    val scopedType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(ClassIds.SCOPED),
        emptyArray(),
        isMarkedNullable = false,
      )
    val outerClassType = outerOwner.defaultType()
    val paramName = outerClassId.shortClassName.identifier.replaceFirstChar { it.lowercase() }
    // Build the dispatch receiver type manually since classSymbol isn't bound to FIR yet
    val dispatchType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(classId),
        emptyArray(),
        isMarkedNullable = false,
      )

    val functionSymbol = FirNamedFunctionSymbol(callableId)

    return buildNamedFunction {
      isLocal = false
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = ContributesMultibindingScopedGeneratorKey.origin
      symbol = functionSymbol
      name = callableId.callableName
      returnTypeRef = scopedType.toFirResolvedTypeRef()
      dispatchReceiverType = dispatchType
      status =
        FirResolvedDeclarationStatusImpl(
          Visibilities.Public,
          Modality.ABSTRACT,
          Visibilities.Public.toEffectiveVisibility(outerOwner, forClass = true),
        )
      this.valueParameters += buildValueParameter {
        resolvePhase = FirResolvePhase.BODY_RESOLVE
        moduleData = session.moduleData
        origin = ContributesMultibindingScopedGeneratorKey.origin
        returnTypeRef = outerClassType.toFirResolvedTypeRef()
        this.name = Name.identifier(paramName)
        symbol = FirValueParameterSymbol()
        containingDeclarationSymbol = functionSymbol
      }
      annotations += buildSimpleAnnotationCall(ClassIds.BINDS, functionSymbol)
      annotations += buildSimpleAnnotationCall(ClassIds.INTO_SET, functionSymbol)
      annotations +=
        buildAnnotationCallWithScope(
          ClassIds.FOR_SCOPE,
          ArgNames.SCOPE,
          scopeArg,
          functionSymbol,
          session,
        )
    }
  }

  @OptIn(DirectDeclarationsAccess::class)
  private fun buildProvidesCompanionObject(
    classId: ClassId,
    outerOwner: FirClassSymbol<*>,
    providesFunction: FirDeclaration,
  ): FirClassLikeSymbol<*> {
    val classSymbol = FirRegularClassSymbol(classId)
    buildRegularClass {
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = ContributesMultibindingScopedGeneratorKey.origin
      source = outerOwner.source
      classKind = ClassKind.OBJECT
      scopeProvider = session.kotlinScopeProvider
      name = SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT
      symbol = classSymbol
      status =
        FirResolvedDeclarationStatusImpl(
            Visibilities.Public,
            Modality.FINAL,
            Visibilities.Public.toEffectiveVisibility(outerOwner, forClass = true),
          )
          .apply { isCompanion = true }
      superTypeRefs += session.builtinTypes.anyType
      declarations += providesFunction
    }
    return classSymbol
  }

  /**
   * Build the `@Provides` function that constructs the scoped class when it is not already
   * injectable and no `@ContributesBinding` self-provider is expected from Metro.
   *
   * Generates: `@Provides fun provideContributedMultibindingScoped(dependency: Dependency):
   * MyScoped`
   */
  @OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
  private fun buildProvidesFunction(
    classId: ClassId,
    scopedSymbol: FirRegularClassSymbol,
  ): FirDeclaration? {
    val callableId = CallableId(classId, Name.identifier(SCOPED_PROVIDER_FUNCTION_NAME))
    val constructorSymbol =
      scopedSymbol.declarationSymbols.filterIsInstance<FirConstructorSymbol>().firstOrNull()
        ?: return null
    val scopedType = scopedSymbol.defaultType()
    val dispatchType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(classId),
        emptyArray(),
        isMarkedNullable = false,
      )

    val functionSymbol = FirNamedFunctionSymbol(callableId)

    return buildNamedFunction {
      isLocal = false
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = ContributesMultibindingScopedGeneratorKey.origin
      symbol = functionSymbol
      name = callableId.callableName
      returnTypeRef = scopedType.toFirResolvedTypeRef()
      dispatchReceiverType = dispatchType
      status =
        FirResolvedDeclarationStatusImpl(
          Visibilities.Public,
          Modality.OPEN,
          Visibilities.Public.toEffectiveVisibility(scopedSymbol, forClass = true),
        )

      for (parameter in constructorSymbol.fir.valueParameters) {
        valueParameters += buildValueParameter {
          resolvePhase = FirResolvePhase.BODY_RESOLVE
          moduleData = session.moduleData
          origin = ContributesMultibindingScopedGeneratorKey.origin
          returnTypeRef = resolveParameterTypeRef(parameter, scopedSymbol)
          this.name = parameter.name
          symbol = FirValueParameterSymbol()
          containingDeclarationSymbol = functionSymbol
          annotations += parameter.annotations
        }
      }

      annotations += buildSimpleAnnotationCall(ClassIds.PROVIDES, functionSymbol)
    }
  }

  @OptIn(DirectDeclarationsAccess::class)
  private fun hasInjectAnnotation(scopedSymbol: FirRegularClassSymbol): Boolean {
    return hasAnnotation(scopedSymbol, ClassIds.INJECT, session) ||
      scopedSymbol.declarationSymbols.filterIsInstance<FirConstructorSymbol>().any {
        it.resolvedCompilerAnnotationsWithClassIds.any { annotation ->
          annotation.toAnnotationClassIdSafe(session) == ClassIds.INJECT
        }
      }
  }

  private fun hasContributesBindingAnnotation(scopedSymbol: FirRegularClassSymbol): Boolean {
    return hasAnnotation(scopedSymbol, ClassIds.CONTRIBUTES_BINDING, session)
  }

  private fun isGeneratedProvidesCompanion(classSymbol: FirClassSymbol<*>): Boolean {
    val parentClassId = classSymbol.classId.outerClassId ?: return false
    return classSymbol.origin == ContributesMultibindingScopedGeneratorKey.origin &&
      classSymbol.classKind == ClassKind.OBJECT &&
      classSymbol.classId.shortClassName == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT &&
      parentClassId.shortClassName == ContributesMultibindingScopedIds.NESTED_INTERFACE_NAME
  }

  private fun needsGeneratedProvidesCompanion(classSymbol: FirClassSymbol<*>): Boolean {
    val scopedSymbol = scopedSymbolForGeneratedContribution(classSymbol) ?: return false
    return needsProvidesFunction(scopedSymbol)
  }

  private fun scopedSymbolForGeneratedContribution(
    classSymbol: FirClassSymbol<*>
  ): FirRegularClassSymbol? {
    if (
      classSymbol.origin != ContributesMultibindingScopedGeneratorKey.origin ||
        classSymbol.classId.shortClassName != ContributesMultibindingScopedIds.NESTED_INTERFACE_NAME
    ) {
      return null
    }
    val scopedClassId = classSymbol.classId.outerClassId ?: return null
    return session.symbolProvider.getClassLikeSymbolByClassId(scopedClassId)
      as? FirRegularClassSymbol
  }

  private fun needsProvidesFunction(scopedSymbol: FirRegularClassSymbol): Boolean {
    return !hasInjectAnnotation(scopedSymbol) && !hasContributesBindingAnnotation(scopedSymbol)
  }

  private fun resolveParameterTypeRef(
    parameter: FirValueParameter,
    ownerSymbol: FirRegularClassSymbol,
  ): FirTypeRef {
    val returnTypeRef = parameter.returnTypeRef
    if (returnTypeRef is FirResolvedTypeRef) return returnTypeRef

    val file = session.firProvider.getFirClassifierContainerFileIfAny(ownerSymbol)
    val scopes =
      if (file != null) {
        createImportingScopes(file, session, ScopeSession())
      } else {
        emptyList()
      }

    return session.typeResolver
      .resolveType(
        typeRef = returnTypeRef,
        configuration =
          TypeResolutionConfiguration(
            scopes = scopes,
            containingClassDeclarations = emptyList(),
            useSiteFile = file,
          ),
        areBareTypesAllowed = true,
        isOperandOfIsOperator = false,
        resolveDeprecations = false,
        supertypeSupplier = SupertypeSupplier.Default,
        expandTypeAliases = false,
      )
      .type
      .toFirResolvedTypeRef()
  }

  /**
   * Build an annotation as [FirAnnotationCall] so Metro recognizes it. Metro's `metroAnnotations()`
   * checks `annotation !is FirAnnotationCall` and skips plain [FirAnnotation] instances.
   */
  @OptIn(DirectDeclarationsAccess::class)
  private fun buildSimpleAnnotationCall(
    classId: ClassId,
    containingSymbol: FirBasedSymbol<*>,
  ): FirAnnotationCall {
    val annotationType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(classId),
        emptyArray(),
        isMarkedNullable = false,
      )
    return buildAnnotationCall {
      annotationTypeRef = annotationType.toFirResolvedTypeRef()
      argumentMapping = buildAnnotationArgumentMapping()
      calleeReference = buildResolvedNamedReference {
        name = classId.shortClassName
        resolvedSymbol =
          session.symbolProvider.getClassLikeSymbolByClassId(classId)!!.let {
            (it as FirClassSymbol<*>)
              .declarationSymbols
              .filterIsInstance<FirConstructorSymbol>()
              .first()
          }
      }
      containingDeclarationSymbol = containingSymbol
      annotationResolvePhase = FirAnnotationResolvePhase.Types
    }
  }

  @AutoService(MetroFirDeclarationGenerationExtension.Factory::class)
  public class Factory : MetroFirDeclarationGenerationExtension.Factory {
    override fun create(
      session: FirSession,
      options: MetroOptions,
      compatContext: CompatContext,
    ): MetroFirDeclarationGenerationExtension = ContributesMultibindingScopedFir(session)
  }
}
