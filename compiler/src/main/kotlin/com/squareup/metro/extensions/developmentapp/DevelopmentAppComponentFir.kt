package com.squareup.metro.extensions.developmentapp

import com.fueledbycaffeine.autoservice.AutoService
import com.squareup.metro.extensions.ArgNames
import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.Keys.DevelopmentAppComponentGeneratorKey
import com.squareup.metro.extensions.fir.buildAnnotationCallWithScope
import com.squareup.metro.extensions.fir.findAnnotation
import com.squareup.metro.extensions.fir.hasAnnotation
import com.squareup.metro.extensions.squareMetroExtensionsConfig
import dev.zacsweers.metro.compiler.MetroOptions
import dev.zacsweers.metro.compiler.api.fir.MetroFirDeclarationGenerationExtension
import dev.zacsweers.metro.compiler.compat.CompatContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildNamedFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClass
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirAnnotationResolvePhase
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCall
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildCollectionLiteral
import org.jetbrains.kotlin.fir.expressions.builder.buildGetClassCall
import org.jetbrains.kotlin.fir.expressions.builder.buildResolvedQualifier
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
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
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Generates nested `MetroComponent` and `MetroComponent.Factory` interfaces for classes annotated
 * with `@DevelopmentAppComponent`.
 *
 * Given:
 * ```
 * @DevelopmentAppComponent
 * class MyApp : DevelopmentApplication()
 * ```
 *
 * This generates:
 * ```
 * @SingleIn(AppScope::class)
 * @DependencyGraph(scope = AppScope::class)
 * interface MetroComponent {
 *   @DependencyGraph.Factory
 *   interface Factory : DevelopmentAppComponent.Factory {
 *     override fun create(@Provides application: Application): MetroComponent
 *   }
 * }
 * ```
 *
 * Metro automatically discovers the generated `@DependencyGraph` interface and generates the
 * implementation class. No `MetroContributionExtension` is needed since this IS the graph, not a
 * contribution to a graph.
 *
 * When `generateLoggedInComponent = false`, the `@DependencyGraph` annotation includes an
 * `excludes` parameter to exclude `LoginScreenModule` and `DevelopmentLoggedInComponent`.
 */
public class DevelopmentAppComponentFir(session: FirSession) :
  MetroFirDeclarationGenerationExtension(session) {

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    if (session.squareMetroExtensionsConfig.enableDevelopmentAppComponent) {
      register(DevelopmentAppComponentIds.PREDICATE)
    }
  }

  override fun getContributionHints(): List<ContributionHint> {
    return session.predicateBasedProvider
      .getSymbolsByPredicate(DevelopmentAppComponentIds.PREDICATE)
      .filterIsInstance<FirRegularClassSymbol>()
      .flatMap { classSymbol: FirRegularClassSymbol ->
        val featureScopeId =
          readClassArgument(classSymbol, "featureScope")
            ?: return@flatMap emptyList<ContributionHint>()
        val parentId = classSymbol.classId
        listOf(
          // FeatureLoginScreenComponent contributes to featureScope
          ContributionHint(
            contributingClassId =
              parentId.createNestedClassId(
                DevelopmentAppComponentIds.FEATURE_LOGIN_SCREEN_COMPONENT_NAME
              ),
            scope = featureScopeId,
          ),
          // NoopLoginScreenComponent contributes to ActivityScope (replaces default)
          ContributionHint(
            contributingClassId =
              parentId.createNestedClassId(
                DevelopmentAppComponentIds.NOOP_LOGIN_SCREEN_COMPONENT_NAME
              ),
            scope = ClassIds.ACTIVITY_SCOPE,
          ),
        )
      }
  }

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext,
  ): Set<Name> {
    // Case 1: The annotated class itself — generate MetroComponent + feature scope types
    if (hasAnnotation(classSymbol, ClassIds.DEVELOPMENT_APP_COMPONENT, session)) {
      val names = mutableSetOf(DevelopmentAppComponentIds.METRO_COMPONENT_NAME)
      if (hasFeatureScope(classSymbol)) {
        names += DevelopmentAppComponentIds.FEATURE_LOGIN_SCREEN_COMPONENT_NAME
        names += DevelopmentAppComponentIds.NOOP_LOGIN_SCREEN_COMPONENT_NAME
      }
      return names
    }

    // Case 2: Our generated MetroComponent — generate Factory inside it.
    // Metro's findCreator() discovers Factory through a scope-based fallback (local Metro fix).
    if (classSymbol.classId.shortClassName == DevelopmentAppComponentIds.METRO_COMPONENT_NAME) {
      val outerClassId = classSymbol.classId.outerClassId ?: return emptySet()
      val outerSymbol =
        session.symbolProvider.getClassLikeSymbolByClassId(outerClassId) as? FirClassSymbol<*>
          ?: return emptySet()
      if (hasAnnotation(outerSymbol, ClassIds.DEVELOPMENT_APP_COMPONENT, session)) {
        return setOf(DevelopmentAppComponentIds.FACTORY_NAME)
      }
    }

    return emptySet()
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): FirClassLikeSymbol<*>? {
    return when (name) {
      DevelopmentAppComponentIds.METRO_COMPONENT_NAME -> generateMetroComponent(owner, name)
      DevelopmentAppComponentIds.FACTORY_NAME -> generateFactory(owner, name)
      DevelopmentAppComponentIds.FEATURE_LOGIN_SCREEN_COMPONENT_NAME ->
        generateFeatureLoginScreenComponent(owner, name)
      DevelopmentAppComponentIds.NOOP_LOGIN_SCREEN_COMPONENT_NAME ->
        generateNoopLoginScreenComponent(owner, name)
      else -> null
    }
  }

  /**
   * Generate the `MetroComponent` interface annotated with `@DependencyGraph(AppScope::class)` and
   * `@SingleIn(AppScope::class)`.
   */
  private fun generateMetroComponent(owner: FirClassSymbol<*>, name: Name): FirClassLikeSymbol<*>? {
    if (!hasAnnotation(owner, ClassIds.DEVELOPMENT_APP_COMPONENT, session)) return null

    val scopeArg = buildAppScopeClassExpression() ?: return null
    val nestedClassId = owner.classId.createNestedClassId(name)
    val classSymbol = FirRegularClassSymbol(nestedClassId)

    val klass = buildRegularClass {
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = DevelopmentAppComponentGeneratorKey.origin
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

      // @DependencyGraph(scope = AppScope::class, excludes = [...])
      annotations += buildDependencyGraphAnnotation(owner, scopeArg, classSymbol)
      // @SingleIn(AppScope::class)
      annotations +=
        buildAnnotationCallWithScope(
          ClassIds.SINGLE_IN,
          ArgNames.SCOPE,
          buildAppScopeClassExpression()!!,
          classSymbol,
          session,
        )
    }

    return klass.symbol
  }

  /**
   * Generate the `Factory` interface inside `MetroComponent`, annotated with
   * `@DependencyGraph.Factory` and extending `DevelopmentAppComponent.Factory`.
   */
  private fun generateFactory(owner: FirClassSymbol<*>, name: Name): FirClassLikeSymbol<*>? {
    // Owner is MetroComponent; its parent should have @DevelopmentAppComponent
    val outerClassId = owner.classId.outerClassId ?: return null
    val outerSymbol =
      session.symbolProvider.getClassLikeSymbolByClassId(outerClassId) as? FirClassSymbol<*>
        ?: return null
    if (!hasAnnotation(outerSymbol, ClassIds.DEVELOPMENT_APP_COMPONENT, session)) return null

    val factoryClassId = owner.classId.createNestedClassId(name)
    val factorySymbol = FirRegularClassSymbol(factoryClassId)

    // Supertype: DevelopmentAppComponent.Factory
    val devAppFactoryType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(ClassIds.DEVELOPMENT_APP_COMPONENT_FACTORY),
        emptyArray(),
        isMarkedNullable = false,
      )

    // Build the create(@Provides application: Application): MetroComponent method
    val createFunction = buildCreateFunction(factoryClassId, owner, factorySymbol)

    val klass = buildRegularClass {
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = DevelopmentAppComponentGeneratorKey.origin
      source = owner.source
      classKind = ClassKind.INTERFACE
      scopeProvider = session.kotlinScopeProvider
      this.name = factoryClassId.shortClassName
      symbol = factorySymbol
      status =
        FirResolvedDeclarationStatusImpl(
          Visibilities.Public,
          Modality.ABSTRACT,
          Visibilities.Public.toEffectiveVisibility(outerSymbol, forClass = true),
        )
      superTypeRefs += session.builtinTypes.anyType
      superTypeRefs += devAppFactoryType.toFirResolvedTypeRef()

      // @DependencyGraph.Factory
      annotations += buildSimpleAnnotationCall(ClassIds.DEPENDENCY_GRAPH_FACTORY, factorySymbol)

      declarations += createFunction
    }

    return klass.symbol
  }

  /**
   * Build `fun create(@Provides application: Application): MetroComponent`.
   *
   * This overrides `DevelopmentAppComponent.Factory.create` with a narrower return type.
   */
  private fun buildCreateFunction(
    factoryClassId: ClassId,
    metroComponentSymbol: FirClassSymbol<*>,
    factorySymbol: FirRegularClassSymbol,
  ): org.jetbrains.kotlin.fir.declarations.FirFunction {
    val callableId = CallableId(factoryClassId, Name.identifier("create"))
    val functionSymbol = FirNamedFunctionSymbol(callableId)

    // Return type: MetroComponent (the parent of Factory)
    val metroComponentType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(metroComponentSymbol.classId),
        emptyArray(),
        isMarkedNullable = false,
      )

    val dispatchType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(factoryClassId),
        emptyArray(),
        isMarkedNullable = false,
      )

    // Parameter type: android.app.Application
    val applicationType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(ClassIds.APPLICATION),
        emptyArray(),
        isMarkedNullable = false,
      )

    return buildNamedFunction {
      isLocal = false
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = DevelopmentAppComponentGeneratorKey.origin
      symbol = functionSymbol
      name = callableId.callableName
      returnTypeRef = metroComponentType.toFirResolvedTypeRef()
      dispatchReceiverType = dispatchType
      status =
        FirResolvedDeclarationStatusImpl(
          Visibilities.Public,
          Modality.ABSTRACT,
          Visibilities.Public.toEffectiveVisibility(metroComponentSymbol, forClass = true),
        )

      // Parameter: @Provides application: Application
      this.valueParameters += buildValueParameter {
        resolvePhase = FirResolvePhase.BODY_RESOLVE
        moduleData = session.moduleData
        origin = DevelopmentAppComponentGeneratorKey.origin
        returnTypeRef = applicationType.toFirResolvedTypeRef()
        this.name = Name.identifier("application")
        symbol = FirValueParameterSymbol()
        containingDeclarationSymbol = functionSymbol
        annotations += buildSimpleAnnotationCall(ClassIds.PROVIDES, functionSymbol)
      }
    }
  }

  // -- Feature scope support --
  // When featureScope/featureComponent are set on @DevelopmentAppComponent, the KSP generator
  // creates three additional types that redirect the DevelopmentLoginScreenComponent from
  // ActivityScope to the feature scope and provide the feature component class. We replicate
  // this behavior here.

  /** Check whether the annotated class has featureScope set (non-Unit). */
  private fun hasFeatureScope(classSymbol: FirClassSymbol<*>): Boolean {
    return readClassArgument(classSymbol, "featureScope") != null
  }

  /** Read a KClass argument from @DevelopmentAppComponent, returning null if absent or Unit. */
  private fun readClassArgument(classSymbol: FirClassSymbol<*>, argName: String): ClassId? {
    val annotation =
      findAnnotation(classSymbol, ClassIds.DEVELOPMENT_APP_COMPONENT, session) ?: return null
    val annotationCall = annotation as? FirAnnotationCall ?: return null
    val name = Name.identifier(argName)

    val rawExpr =
      annotationCall.argumentMapping.mapping[name]
        ?: annotationCall.argumentList.arguments
          .filterIsInstance<FirNamedArgumentExpression>()
          .firstOrNull { it.name == name }
          ?.expression

    val getClassCall =
      rawExpr as? org.jetbrains.kotlin.fir.expressions.FirGetClassCall ?: return null
    val innerArg = getClassCall.argumentList.arguments.firstOrNull() ?: return null

    // Resolve using the same strategy as extractScopeClassId in FirHelpers:
    // try FirResolvedQualifier first, then FirPropertyAccessExpression with import scanning.
    val classId =
      when (innerArg) {
        is org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier -> innerArg.classId
        is org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression -> {
          val ref = innerArg.calleeReference
          if (
            ref is org.jetbrains.kotlin.fir.references.FirResolvedNamedReference &&
              ref.resolvedSymbol is org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol<*>
          ) {
            (ref.resolvedSymbol as org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol<*>)
              .classId
          } else {
            // Scan the containing file's imports for a matching simple name
            val simpleName = ref.name
            val regSymbol = classSymbol as? FirRegularClassSymbol
            val file = regSymbol?.let { session.firProvider.getFirClassifierContainerFileIfAny(it) }
            val fromImports =
              file?.imports?.firstNotNullOfOrNull { import ->
                if (import.isAllUnder) return@firstNotNullOfOrNull null
                val importedFqName = import.importedFqName ?: return@firstNotNullOfOrNull null
                if (importedFqName.shortName() == simpleName) {
                  val cid = ClassId.topLevel(importedFqName)
                  session.symbolProvider.getClassLikeSymbolByClassId(cid)?.classId
                } else null
              }
            // Also check the same package as the annotated class
            fromImports
              ?: session.symbolProvider
                .getClassLikeSymbolByClassId(ClassId(classSymbol.classId.packageFqName, simpleName))
                ?.classId
          }
        }
        else -> null
      }

    // Unit is used as the default / "not set" value
    if (classId?.asSingleFqName()?.asString() == "kotlin.Unit") return null
    return classId
  }

  /**
   * Generate `@ContributesTo(featureScope)` interface extending `DevelopmentLoginScreenComponent`.
   * This moves the FeatureProvider accessor from ActivityScope to the feature scope.
   */
  private fun generateFeatureLoginScreenComponent(
    owner: FirClassSymbol<*>,
    name: Name,
  ): FirClassLikeSymbol<*>? {
    val featureScopeId = readClassArgument(owner, "featureScope") ?: return null
    val nestedClassId = owner.classId.createNestedClassId(name)
    val classSymbol = FirRegularClassSymbol(nestedClassId)

    // Supertype: DevelopmentLoginScreenComponent
    val loginScreenType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(ClassIds.DEVELOPMENT_LOGIN_SCREEN_COMPONENT),
        emptyArray(),
        isMarkedNullable = false,
      )

    buildRegularClass {
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = DevelopmentAppComponentGeneratorKey.origin
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
      superTypeRefs += loginScreenType.toFirResolvedTypeRef()

      val featureScopeExpr = buildScopeClassExpression(featureScopeId) ?: return null
      annotations +=
        buildAnnotationCallWithScope(
          ClassIds.CONTRIBUTES_TO,
          ArgNames.SCOPE,
          featureScopeExpr,
          classSymbol,
          session,
        )
    }

    return classSymbol
  }

  /**
   * Generate `@ContributesTo(ActivityScope, replaces=[ContributedDevelopmentLoginScreenComponent])`
   * empty interface. This removes the default login screen component from ActivityScope.
   */
  private fun generateNoopLoginScreenComponent(
    owner: FirClassSymbol<*>,
    name: Name,
  ): FirClassLikeSymbol<*>? {
    if (!hasFeatureScope(owner)) return null
    val nestedClassId = owner.classId.createNestedClassId(name)
    val classSymbol = FirRegularClassSymbol(nestedClassId)

    buildRegularClass {
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = DevelopmentAppComponentGeneratorKey.origin
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

      // @ContributesTo(scope = ActivityScope::class, replaces =
      // [ContributedDevelopmentLoginScreenComponent::class])
      val scopeExpr = buildScopeClassExpression(ClassIds.ACTIVITY_SCOPE) ?: return null
      val replacesArray =
        buildExcludesArrayLiteral(listOf(ClassIds.CONTRIBUTED_DEVELOPMENT_LOGIN_SCREEN_COMPONENT))
      annotations +=
        buildContributesToWithReplaces(scopeExpr, replacesArray, classSymbol) ?: return null
    }

    return classSymbol
  }

  /**
   * Generate `@ContributesTo(ActivityScope, replaces=[DefaultFeatureModule]) @Module` object
   * providing the feature component class. This is an object with a @Provides method.
   */
  private fun generateFeatureModule(owner: FirClassSymbol<*>, name: Name): FirClassLikeSymbol<*>? {
    if (!hasFeatureScope(owner)) return null
    val featureComponentId = readClassArgument(owner, "featureComponent") ?: return null
    // Only generate if all needed types are on the classpath
    if (session.symbolProvider.getClassLikeSymbolByClassId(ClassIds.ACTIVITY_SCOPE) == null)
      return null
    if (session.symbolProvider.getClassLikeSymbolByClassId(ClassIds.DEFAULT_FEATURE_MODULE) == null)
      return null
    val nestedClassId = owner.classId.createNestedClassId(name)
    val classSymbol = FirRegularClassSymbol(nestedClassId)

    buildRegularClass {
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = DevelopmentAppComponentGeneratorKey.origin
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

      // @ContributesTo(scope = ActivityScope::class, replaces = [DefaultFeatureModule::class])
      val scopeExpr = buildScopeClassExpression(ClassIds.ACTIVITY_SCOPE) ?: return null
      val replacesArray = buildExcludesArrayLiteral(listOf(ClassIds.DEFAULT_FEATURE_MODULE))
      annotations +=
        buildContributesToWithReplaces(scopeExpr, replacesArray, classSymbol) ?: return null

      // @Module (Dagger annotation — only if on classpath)
      session.symbolProvider.getClassLikeSymbolByClassId(ClassIds.DAGGER_MODULE)?.let {
        annotations += buildSimpleAnnotationCall(ClassIds.DAGGER_MODULE, classSymbol)
      }

      // @Provides @DevelopmentFeatureScopeComponent fun provideFeatureScopeComponent(): KClass<*>?
      declarations +=
        buildProvideFeatureScopeComponentFunction(nestedClassId, classSymbol, featureComponentId)
    }

    return classSymbol
  }

  /**
   * Build `@Provides @DevelopmentFeatureScopeComponent fun provideFeatureScopeComponent():
   * KClass<*>?` that returns the feature component class. The body is not needed — Metro treats
   * this as an abstract @Provides in a binding container (interface), so the graph implementation
   * provides it. However, since we want it to return a constant, we make it a default method in the
   * interface. For FIR, we just declare the signature; the IR extension would need to fill in the
   * body.
   *
   * Actually, Metro's binding containers with @Provides are handled as abstract declarations whose
   * return value is provided by the graph. We just need the signature with the right annotations
   * and return type.
   */
  private fun buildProvideFeatureScopeComponentFunction(
    featureModuleClassId: ClassId,
    featureModuleSymbol: FirRegularClassSymbol,
    featureComponentId: ClassId,
  ): org.jetbrains.kotlin.fir.declarations.FirFunction {
    val callableId =
      CallableId(featureModuleClassId, Name.identifier("provideFeatureScopeComponent"))
    val functionSymbol = FirNamedFunctionSymbol(callableId)

    // Return type: KClass<*>? (nullable)
    val kClassClassId = ClassId(FqName("kotlin.reflect"), Name.identifier("KClass"))
    val starProjection = org.jetbrains.kotlin.fir.types.ConeStarProjection
    val kClassStarType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(kClassClassId),
        arrayOf(starProjection),
        isMarkedNullable = true,
      )

    val dispatchType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(featureModuleClassId),
        emptyArray(),
        isMarkedNullable = false,
      )

    return buildNamedFunction {
      isLocal = false
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = DevelopmentAppComponentGeneratorKey.origin
      symbol = functionSymbol
      name = callableId.callableName
      returnTypeRef = kClassStarType.toFirResolvedTypeRef()
      dispatchReceiverType = dispatchType
      status =
        FirResolvedDeclarationStatusImpl(
          Visibilities.Public,
          Modality.ABSTRACT,
          Visibilities.Public.toEffectiveVisibility(featureModuleSymbol, forClass = true),
        )

      // @Provides
      this.annotations += buildSimpleAnnotationCall(ClassIds.PROVIDES, functionSymbol)
      // @DevelopmentFeatureScopeComponent (qualifier)
      if (
        session.symbolProvider.getClassLikeSymbolByClassId(
          ClassIds.DEVELOPMENT_FEATURE_SCOPE_COMPONENT
        ) != null
      ) {
        this.annotations +=
          buildSimpleAnnotationCall(ClassIds.DEVELOPMENT_FEATURE_SCOPE_COMPONENT, functionSymbol)
      }
    }
  }

  /** Build a `ScopeClass::class` expression from a ClassId. */
  private fun buildScopeClassExpression(scopeClassId: ClassId): FirExpression? {
    val scopeType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(scopeClassId),
        emptyArray(),
        isMarkedNullable = false,
      )
    val kClassClassId = ClassId(FqName("kotlin.reflect"), Name.identifier("KClass"))
    val kClassType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(kClassClassId),
        arrayOf(scopeType),
        isMarkedNullable = false,
      )
    val scopeSymbol =
      session.symbolProvider.getClassLikeSymbolByClassId(scopeClassId) ?: return null
    return buildGetClassCall {
      coneTypeOrNull = kClassType
      argumentList = buildArgumentList {
        arguments += buildResolvedQualifier {
          packageFqName = scopeClassId.packageFqName
          relativeClassFqName = scopeClassId.relativeClassName
          coneTypeOrNull = scopeType
          symbol = scopeSymbol
          resolvedToCompanionObject = false
        }
      }
    }
  }

  /** Build `@ContributesTo(scope = X, replaces = [...])` annotation. */
  @OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
  private fun buildContributesToWithReplaces(
    scopeArg: FirExpression,
    replacesArg: FirExpression,
    containingSymbol: FirBasedSymbol<*>,
  ): FirAnnotationCall? {
    val annotationType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(ClassIds.CONTRIBUTES_TO),
        emptyArray(),
        isMarkedNullable = false,
      )
    val annotationClassSymbol =
      session.symbolProvider.getClassLikeSymbolByClassId(ClassIds.CONTRIBUTES_TO) ?: return null
    val constructorSymbol =
      (annotationClassSymbol as FirClassSymbol<*>)
        .declarationSymbols
        .filterIsInstance<FirConstructorSymbol>()
        .first()
    val scopeParam = constructorSymbol.fir.valueParameters.first { it.name == ArgNames.SCOPE }
    val replacesParam =
      constructorSymbol.fir.valueParameters.first { it.name == Name.identifier("replaces") }

    return buildAnnotationCall {
      annotationTypeRef = annotationType.toFirResolvedTypeRef()
      argumentMapping = buildAnnotationArgumentMapping {
        mapping[ArgNames.SCOPE] = scopeArg
        mapping[Name.identifier("replaces")] = replacesArg
      }
      argumentList =
        buildResolvedArgumentList(
          original = null,
          mapping = linkedMapOf(scopeArg to scopeParam, replacesArg to replacesParam),
        )
      calleeReference = buildResolvedNamedReference {
        name = ClassIds.CONTRIBUTES_TO.shortClassName
        resolvedSymbol = constructorSymbol
      }
      containingDeclarationSymbol = containingSymbol
      annotationResolvePhase = FirAnnotationResolvePhase.Types
    }
  }

  /**
   * Build `@DependencyGraph(scope = AppScope::class)` or `@DependencyGraph(scope = AppScope::class,
   * excludes = [...])`.
   */
  @OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
  private fun buildDependencyGraphAnnotation(
    annotatedClass: FirClassSymbol<*>,
    scopeArg: FirExpression,
    containingSymbol: FirBasedSymbol<*>,
  ): FirAnnotationCall {
    val annotationType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(ClassIds.DEPENDENCY_GRAPH),
        emptyArray(),
        isMarkedNullable = false,
      )
    val annotationClassSymbol =
      session.symbolProvider.getClassLikeSymbolByClassId(ClassIds.DEPENDENCY_GRAPH)!!
    val constructorSymbol =
      (annotationClassSymbol as FirClassSymbol<*>)
        .declarationSymbols
        .filterIsInstance<FirConstructorSymbol>()
        .first()
    val scopeParam = constructorSymbol.fir.valueParameters.first { it.name == ArgNames.SCOPE }

    val generateLoggedIn = readGenerateLoggedInComponent(annotatedClass)

    if (generateLoggedIn) {
      // Simple case: just scope
      return buildAnnotationCall {
        annotationTypeRef = annotationType.toFirResolvedTypeRef()
        argumentMapping = buildAnnotationArgumentMapping { mapping[ArgNames.SCOPE] = scopeArg }
        argumentList =
          buildResolvedArgumentList(original = null, mapping = linkedMapOf(scopeArg to scopeParam))
        calleeReference = buildResolvedNamedReference {
          name = ClassIds.DEPENDENCY_GRAPH.shortClassName
          resolvedSymbol = constructorSymbol
        }
        containingDeclarationSymbol = containingSymbol
        annotationResolvePhase = FirAnnotationResolvePhase.Types
      }
    }

    // With excludes: @DependencyGraph(scope = AppScope::class, excludes = [...])
    // Only include excludes for classes that are actually on the classpath.
    val excludeClassIds =
      listOf(ClassIds.LOGIN_SCREEN_MODULE, ClassIds.DEVELOPMENT_LOGGED_IN_COMPONENT)
    val resolvableExcludes = excludeClassIds.filter {
      session.symbolProvider.getClassLikeSymbolByClassId(it) != null
    }

    if (resolvableExcludes.isEmpty()) {
      // No excluded classes found on classpath — emit scope-only annotation
      return buildAnnotationCall {
        annotationTypeRef = annotationType.toFirResolvedTypeRef()
        argumentMapping = buildAnnotationArgumentMapping { mapping[ArgNames.SCOPE] = scopeArg }
        argumentList =
          buildResolvedArgumentList(original = null, mapping = linkedMapOf(scopeArg to scopeParam))
        calleeReference = buildResolvedNamedReference {
          name = ClassIds.DEPENDENCY_GRAPH.shortClassName
          resolvedSymbol = constructorSymbol
        }
        containingDeclarationSymbol = containingSymbol
        annotationResolvePhase = FirAnnotationResolvePhase.Types
      }
    }

    val excludesParam = constructorSymbol.fir.valueParameters.first { it.name == ArgNames.EXCLUDES }
    val excludesArray = buildExcludesArrayLiteral(resolvableExcludes)

    return buildAnnotationCall {
      annotationTypeRef = annotationType.toFirResolvedTypeRef()
      argumentMapping = buildAnnotationArgumentMapping {
        mapping[ArgNames.SCOPE] = scopeArg
        mapping[ArgNames.EXCLUDES] = excludesArray
      }
      argumentList =
        buildResolvedArgumentList(
          original = null,
          mapping = linkedMapOf(scopeArg to scopeParam, excludesArray to excludesParam),
        )
      calleeReference = buildResolvedNamedReference {
        name = ClassIds.DEPENDENCY_GRAPH.shortClassName
        resolvedSymbol = constructorSymbol
      }
      containingDeclarationSymbol = containingSymbol
      annotationResolvePhase = FirAnnotationResolvePhase.Types
    }
  }

  /** Read the `generateLoggedInComponent` boolean from the annotation (defaults to `true`). */
  private fun readGenerateLoggedInComponent(classSymbol: FirClassSymbol<*>): Boolean {
    val annotation =
      findAnnotation(classSymbol, ClassIds.DEVELOPMENT_APP_COMPONENT, session) ?: return true
    val annotationCall = annotation as? FirAnnotationCall ?: return true

    val argName = Name.identifier("generateLoggedInComponent")

    // Try argument mapping first
    val mappedValue = annotationCall.argumentMapping.mapping[argName]
    if (mappedValue != null) {
      return extractBooleanConst(mappedValue) ?: true
    }

    // Fall back to argument list (named arguments)
    for (arg in annotationCall.argumentList.arguments) {
      if (arg is FirNamedArgumentExpression && arg.name == argName) {
        return extractBooleanConst(arg.expression) ?: true
      }
    }

    return true
  }

  /** Extract a boolean constant from a FIR expression. */
  private fun extractBooleanConst(expr: FirExpression): Boolean? {
    if (expr is org.jetbrains.kotlin.fir.expressions.FirLiteralExpression) {
      return expr.value as? Boolean
    }
    return null
  }

  /** Build an array literal of class references for the `excludes` parameter. */
  private fun buildExcludesArrayLiteral(classIds: List<ClassId>): FirExpression {
    val kClassClassId = ClassId(FqName("kotlin.reflect"), Name.identifier("KClass"))

    val getClassCalls = classIds.mapNotNull { classId ->
      val classType =
        ConeClassLikeTypeImpl(
          ConeClassLikeLookupTagImpl(classId),
          emptyArray(),
          isMarkedNullable = false,
        )
      val kClassType =
        ConeClassLikeTypeImpl(
          ConeClassLikeLookupTagImpl(kClassClassId),
          arrayOf(classType),
          isMarkedNullable = false,
        )
      val resolvedSymbol =
        session.symbolProvider.getClassLikeSymbolByClassId(classId) ?: return@mapNotNull null

      buildGetClassCall {
        coneTypeOrNull = kClassType
        argumentList = buildArgumentList {
          arguments += buildResolvedQualifier {
            packageFqName = classId.packageFqName
            relativeClassFqName = classId.relativeClassName
            coneTypeOrNull = classType
            symbol = resolvedSymbol
            resolvedToCompanionObject = false
          }
        }
      }
    }

    return buildCollectionLiteral {
      coneTypeOrNull = session.builtinTypes.anyType.coneType
      argumentList = buildArgumentList {
        for (call in getClassCalls) {
          arguments += call
        }
      }
    }
  }

  /** Build a synthetic `AppScope::class` expression for the hardcoded scope. */
  private fun buildAppScopeClassExpression(): FirExpression? {
    val appScopeClassId = ClassIds.APP_SCOPE
    val appScopeType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(appScopeClassId),
        emptyArray(),
        isMarkedNullable = false,
      )
    val kClassClassId = ClassId(FqName("kotlin.reflect"), Name.identifier("KClass"))
    val kClassType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(kClassClassId),
        arrayOf(appScopeType),
        isMarkedNullable = false,
      )
    val appScopeSymbol =
      session.symbolProvider.getClassLikeSymbolByClassId(appScopeClassId) ?: return null

    return buildGetClassCall {
      coneTypeOrNull = kClassType
      argumentList = buildArgumentList {
        arguments += buildResolvedQualifier {
          packageFqName = appScopeClassId.packageFqName
          relativeClassFqName = appScopeClassId.relativeClassName
          coneTypeOrNull = appScopeType
          symbol = appScopeSymbol
          resolvedToCompanionObject = false
        }
      }
    }
  }

  /**
   * Build an annotation as [FirAnnotationCall] so Metro recognizes it. Metro's `metroAnnotations()`
   * checks `annotation !is FirAnnotationCall` and skips plain `FirAnnotation` instances.
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
    ): MetroFirDeclarationGenerationExtension = DevelopmentAppComponentFir(session)
  }
}
