package com.squareup.metro.extensions.scoped

import com.fueledbycaffeine.autoservice.AutoService
import com.squareup.metro.extensions.ArgNames
import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.Keys.ContributesMultibindingScopedGeneratorKey
import com.squareup.metro.extensions.fir.buildAnnotationCallWithScope
import com.squareup.metro.extensions.fir.buildClassExpression
import com.squareup.metro.extensions.fir.extractScopeClassId
import com.squareup.metro.extensions.fir.hasAnnotation
import com.squareup.metro.extensions.fir.resolveValueParameterTypeRef
import dev.zacsweers.metro.compiler.MetroOptions
import dev.zacsweers.metro.compiler.api.fir.MetroFirDeclarationGenerationExtension
import dev.zacsweers.metro.compiler.compat.CompatContext
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
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
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirAnnotationResolvePhase
import org.jetbrains.kotlin.fir.expressions.FirArgumentList
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCall
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.references.FirReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
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
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.types.ConstantValueKind

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
public class ContributesMultibindingScopedFir(
  session: FirSession,
  private val compatContext: CompatContext,
) : MetroFirDeclarationGenerationExtension(session) {

  private val annotatedScopedClasses by lazy {
    session.predicateBasedProvider
      .getSymbolsByPredicate(ContributesMultibindingScopedIds.PREDICATE)
      .filterIsInstance<FirRegularClassSymbol>()
      .toList()
  }

  private val generatedHolderClassIds by lazy {
    annotatedScopedClasses.associateBy { classSymbol ->
      ContributesMultibindingScopedIds.holderClassId(classSymbol.classId)
    }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(ContributesMultibindingScopedIds.PREDICATE)
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> {
    return generatedHolderClassIds.keys
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
    val scopedSymbol = generatedHolderClassIds[classId] ?: return null
    val scopeClassId =
      extractScopeClassId(
        scopedSymbol,
        ContributesMultibindingScopedIds.CONTRIBUTES_MULTIBINDING_SCOPED_CLASS_ID,
        session,
      ) ?: return null
    val scopeSymbol =
      session.symbolProvider.getClassLikeSymbolByClassId(scopeClassId) as? FirClassSymbol<*>
        ?: return null
    val scopeArg = buildClassExpression(scopeSymbol, session)
    val classSymbol = FirRegularClassSymbol(classId)

    return buildRegularClass {
        resolvePhase = FirResolvePhase.BODY_RESOLVE
        moduleData = session.moduleData
        origin = ContributesMultibindingScopedGeneratorKey.origin
        classKind = ClassKind.INTERFACE
        scopeProvider = session.kotlinScopeProvider
        name = classId.shortClassName
        symbol = classSymbol
        status =
          FirResolvedDeclarationStatusImpl(
            Visibilities.Public,
            Modality.ABSTRACT,
            Visibilities.Public.toEffectiveVisibility(scopedSymbol, forClass = true),
          )
        superTypeRefs += session.builtinTypes.anyType
        annotations +=
          NonAcceptingAnnotationCall(
            compatContext,
            buildAnnotationCallWithScope(
              ClassIds.CONTRIBUTES_TO,
              ArgNames.SCOPE,
              scopeArg,
              classSymbol,
              session,
            ),
            classSymbol,
          )
        annotations += buildSimpleAnnotationCall(ClassIds.BINDING_CONTAINER, classSymbol)
        annotations +=
          NonAcceptingAnnotationCall(
            compatContext,
            buildAnnotationCallWithScope(
              ClassIds.ORIGIN,
              ArgNames.VALUE,
              buildClassExpression(scopedSymbol, session),
              classSymbol,
              session,
            ),
            classSymbol,
          )
      }
      .symbol
  }

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext,
  ): Set<Name> {
    generatedHolderClassIds[classSymbol.classId]?.let { scopedSymbol ->
      return buildSet {
        add(ContributesMultibindingScopedIds.NESTED_INTERFACE_NAME)
        if (needsProvidesFunction(scopedSymbol)) add(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
      }
    }
    return emptySet()
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): FirClassLikeSymbol<*>? {
    if (name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) {
      val scopedSymbol = generatedHolderClassIds[owner.classId] ?: return null
      if (!needsProvidesFunction(scopedSymbol)) return null
      val companionClassId = owner.classId.createNestedClassId(name)
      val providerFunctions = buildList {
        buildProvidesFunction(companionClassId, scopedSymbol)?.let(::add)
      }
      return buildProvidesCompanionObject(companionClassId, owner, providerFunctions)
    }

    if (name != ContributesMultibindingScopedIds.NESTED_INTERFACE_NAME) return null
    val scopedSymbol = generatedHolderClassIds[owner.classId] ?: return null
    val scopeClassId =
      extractScopeClassId(
        scopedSymbol,
        ContributesMultibindingScopedIds.CONTRIBUTES_MULTIBINDING_SCOPED_CLASS_ID,
        session,
      ) ?: return null
    val scopeSymbol =
      session.symbolProvider.getClassLikeSymbolByClassId(scopeClassId) as? FirClassSymbol<*>
        ?: return null
    val scopeArg = buildClassExpression(scopeSymbol, session)
    val shouldGenerateBinds = !hasLegacyScopedProviders(scopedSymbol)

    val nestedClassId = owner.classId.createNestedClassId(name)
    val classSymbol = FirRegularClassSymbol(nestedClassId)
    val declarations = buildList {
      if (shouldGenerateBinds) {
        add(buildBindsFunction(nestedClassId, scopedSymbol, scopeArg))
      }
    }

    return buildRegularClass {
        resolvePhase = FirResolvePhase.BODY_RESOLVE
        moduleData = session.moduleData
        origin = ContributesMultibindingScopedGeneratorKey.origin
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
            classSymbol,
            session,
          )
        annotations += buildSimpleAnnotationCall(ClassIds.BINDING_CONTAINER, classSymbol)
        annotations +=
          buildAnnotationCallWithScope(
            ClassIds.ORIGIN,
            ArgNames.VALUE,
            buildClassExpression(scopedSymbol, session),
            classSymbol,
            session,
          )
        this.declarations += declarations
      }
      .symbol
  }

  override fun getContributionHints(): List<ContributionHint> {
    return annotatedScopedClasses
      .mapNotNull { classSymbol ->
        val scopeClassId =
          extractScopeClassId(
            classSymbol,
            ContributesMultibindingScopedIds.CONTRIBUTES_MULTIBINDING_SCOPED_CLASS_ID,
            session,
          ) ?: return@mapNotNull null
        buildList<ContributionHint> {
          add(
            ContributionHint(
              contributingClassId =
                ContributesMultibindingScopedIds.holderClassId(classSymbol.classId),
              scope = scopeClassId,
            )
          )
          add(
            ContributionHint(
              contributingClassId =
                ContributesMultibindingScopedIds.contributionClassId(classSymbol.classId),
              scope = scopeClassId,
            )
          )
        }
      }
      .flatten()
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    return if (isGeneratedHolderCompanion(classSymbol)) {
      setOf(SpecialNames.INIT)
    } else {
      emptySet()
    }
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
    return if (isGeneratedHolderCompanion(context.owner)) {
      listOf(
        createConstructor(
            context.owner,
            ContributesMultibindingScopedGeneratorKey,
            isPrimary = true,
            generateDelegatedNoArgConstructorCall = true,
          ) {
            visibility = Visibilities.Private
          }
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
    providesFunctions: List<FirDeclaration>,
  ): FirClassLikeSymbol<*> {
    val classSymbol = FirRegularClassSymbol(classId)
    buildRegularClass {
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = ContributesMultibindingScopedGeneratorKey.origin
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
      declarations += providesFunctions
    }
    return classSymbol
  }

  /**
   * Build the `@Provides` function that constructs the scoped class when it is not already
   * injectable.
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
        valueParameters +=
          with(compatContext) {
            buildValueParameterCopyCompat(parameter) {
              resolvePhase = FirResolvePhase.BODY_RESOLVE
              moduleData = session.moduleData
              origin = ContributesMultibindingScopedGeneratorKey.origin
              returnTypeRef = resolveValueParameterTypeRef(parameter, scopedSymbol, session)
              this.name = parameter.name
              symbol = FirValueParameterSymbol()
              containingDeclarationSymbol = functionSymbol
              annotations.clear()
              annotations += parameter.annotations
              if (parameter.hasNullDefaultValue()) {
                defaultValue =
                  buildLiteralExpression(
                    source = null,
                    kind = ConstantValueKind.Null,
                    value = null,
                    setType = true,
                  )
              }
              source = null
            }
          }
      }

      annotations += buildSimpleAnnotationCall(ClassIds.PROVIDES, functionSymbol)
      annotations += buildScopeAnnotationCopies(scopedSymbol, functionSymbol)
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

  private fun FirValueParameter.hasNullDefaultValue(): Boolean {
    return (defaultValue as? FirLiteralExpression)?.kind == ConstantValueKind.Null
  }

  private fun isGeneratedHolderCompanion(classSymbol: FirClassSymbol<*>): Boolean {
    val parentClassId = classSymbol.classId.outerClassId ?: return false
    return classSymbol.origin == ContributesMultibindingScopedGeneratorKey.origin &&
      classSymbol.classKind == ClassKind.OBJECT &&
      classSymbol.classId.shortClassName == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT &&
      parentClassId in generatedHolderClassIds
  }

  private fun hasLegacyScopedProviders(scopedSymbol: FirRegularClassSymbol): Boolean {
    val legacyProviderClassId =
      ContributesMultibindingScopedIds.legacyScopedProvidersClassId(scopedSymbol.classId)
    return session.symbolProvider.getClassLikeSymbolByClassId(legacyProviderClassId) != null
  }

  private fun needsProvidesFunction(scopedSymbol: FirRegularClassSymbol): Boolean {
    return !hasInjectAnnotation(scopedSymbol)
  }

  private fun buildScopeAnnotationCopies(
    scopedSymbol: FirRegularClassSymbol,
    containingSymbol: FirBasedSymbol<*>,
  ): List<FirAnnotationCall> {
    return scopedSymbol.resolvedCompilerAnnotationsWithClassIds.mapNotNull { annotation ->
      val annotationCall = annotation as? FirAnnotationCall ?: return@mapNotNull null
      if (!isScopeAnnotation(annotation)) return@mapNotNull null
      NonAcceptingAnnotationCall(compatContext, annotationCall, containingSymbol)
    }
  }

  private fun isScopeAnnotation(annotation: FirAnnotationCall): Boolean {
    val annotationClassId = annotation.toAnnotationClassIdSafe(session) ?: return false
    if (annotationClassId == ClassIds.SINGLE_IN) return true

    val annotationClass =
      session.symbolProvider.getClassLikeSymbolByClassId(annotationClassId)
        as? FirRegularClassSymbol ?: return false
    return annotationClass.resolvedCompilerAnnotationsWithClassIds.any { metaAnnotation ->
      metaAnnotation.toAnnotationClassIdSafe(session) in ClassIds.SCOPE_CLASS_IDS
    }
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
    ): MetroFirDeclarationGenerationExtension =
      ContributesMultibindingScopedFir(session, compatContext)
  }
}

private class NonAcceptingAnnotationCall(
  private val compatContext: CompatContext,
  private val delegate: FirAnnotationCall,
  override val containingDeclarationSymbol: FirBasedSymbol<*>,
) : FirAnnotationCall() {
  override val source: KtSourceElement?
    get() =
      with(compatContext) { delegate.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated) }

  @UnresolvedExpressionTypeAccess
  override val coneTypeOrNull: ConeKotlinType?
    get() = delegate.coneTypeOrNull

  override val annotations: List<FirAnnotation>
    get() = delegate.annotations

  override val useSiteTarget: AnnotationUseSiteTarget?
    get() = delegate.useSiteTarget

  override val annotationTypeRef: FirTypeRef
    get() = delegate.annotationTypeRef

  override val typeArguments: List<FirTypeProjection>
    get() = delegate.typeArguments

  override val argumentList: FirArgumentList
    get() = delegate.argumentList

  override val calleeReference: FirReference
    get() = delegate.calleeReference

  override val argumentMapping: FirAnnotationArgumentMapping
    get() = delegate.argumentMapping

  override val annotationResolvePhase: FirAnnotationResolvePhase
    get() = delegate.annotationResolvePhase

  override fun replaceConeTypeOrNull(newConeTypeOrNull: ConeKotlinType?) {
    delegate.replaceConeTypeOrNull(newConeTypeOrNull)
  }

  override fun replaceAnnotations(newAnnotations: List<FirAnnotation>) {
    delegate.replaceAnnotations(newAnnotations)
  }

  override fun replaceUseSiteTarget(newUseSiteTarget: AnnotationUseSiteTarget?) {
    delegate.replaceUseSiteTarget(newUseSiteTarget)
  }

  override fun replaceAnnotationTypeRef(newAnnotationTypeRef: FirTypeRef) {
    delegate.replaceAnnotationTypeRef(newAnnotationTypeRef)
  }

  override fun replaceTypeArguments(newTypeArguments: List<FirTypeProjection>) {
    delegate.replaceTypeArguments(newTypeArguments)
  }

  override fun replaceArgumentList(newArgumentList: FirArgumentList) {
    delegate.replaceArgumentList(newArgumentList)
  }

  override fun replaceCalleeReference(newCalleeReference: FirReference) {
    delegate.replaceCalleeReference(newCalleeReference)
  }

  override fun replaceArgumentMapping(newArgumentMapping: FirAnnotationArgumentMapping) {
    delegate.replaceArgumentMapping(newArgumentMapping)
  }

  override fun replaceAnnotationResolvePhase(newAnnotationResolvePhase: FirAnnotationResolvePhase) {
    delegate.replaceAnnotationResolvePhase(newAnnotationResolvePhase)
  }

  override fun <D> transformAnnotations(transformer: FirTransformer<D>, data: D) =
    delegate.transformAnnotations(transformer, data)

  override fun <D> transformAnnotationTypeRef(transformer: FirTransformer<D>, data: D) =
    delegate.transformAnnotationTypeRef(transformer, data)

  override fun <D> transformTypeArguments(transformer: FirTransformer<D>, data: D) =
    delegate.transformTypeArguments(transformer, data)

  override fun <D> transformCalleeReference(transformer: FirTransformer<D>, data: D) =
    delegate.transformCalleeReference(transformer, data)

  override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) = Unit

  override fun <D> transformChildren(transformer: FirTransformer<D>, data: D) =
    delegate.transformChildren(transformer, data)
}
