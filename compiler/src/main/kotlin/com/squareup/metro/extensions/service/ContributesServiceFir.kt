package com.squareup.metro.extensions.service

import com.fueledbycaffeine.autoservice.AutoService
import com.squareup.metro.extensions.ArgNames
import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.Keys.ContributesServiceGeneratorKey
import com.squareup.metro.extensions.fir.buildAnnotationCallWithScope
import com.squareup.metro.extensions.fir.buildClassExpression
import com.squareup.metro.extensions.fir.extractClassIdsFromArrayArg
import com.squareup.metro.extensions.fir.extractScopeArgument
import com.squareup.metro.extensions.fir.extractScopeClassId
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
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildNamedFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClass
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirAnnotationResolvePhase
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCall
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildCollectionLiteral
import org.jetbrains.kotlin.fir.expressions.builder.buildGetClassCall
import org.jetbrains.kotlin.fir.expressions.builder.buildResolvedQualifier
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
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

private const val FAKE_SERVICE_PROVIDER_FUNCTION_NAME = "provideContributedServiceReplacement"

/**
 * Generates a nested `ServiceContribution` binding container object for classes annotated with
 * `@ContributesService`.
 *
 * Given a **real service** in a non-release build:
 * ```
 * @ContributesService(SomeScope::class)
 * @SomeQualifier
 * interface MyService
 * ```
 *
 * This generates:
 * ```
 * @ContributesTo(SomeScope::class)
 * @BindingContainer
 * object ServiceContribution {
 *   @Provides @SingleIn(SomeScope::class)
 *   fun provideMyService(
 *     @SomeQualifier serviceCreator: ServiceCreator,
 *     @FakeMode isFakeMode: Boolean,
 *   ): MyService
 * }
 * ```
 *
 * In a **release build**, the `@FakeMode isFakeMode` parameter is omitted:
 * ```
 * @ContributesTo(SomeScope::class)
 * @BindingContainer
 * object ServiceContribution {
 *   @Provides @SingleIn(SomeScope::class)
 *   fun provideMyService(
 *     @SomeQualifier serviceCreator: ServiceCreator,
 *   ): MyService
 * }
 * ```
 *
 * Given a **fake service**:
 * ```
 * @ContributesService(SomeScope::class, replaces = [MyService::class])
 * class FakeMyService(dependency: Dependency) : MyService
 * ```
 *
 * This generates:
 * ```
 * @ContributesTo(
 *   SomeScope::class,
 *   replaces = [MyService::class, MyService.ServiceContribution::class],
 * )
 * @BindingContainer
 * object ServiceContribution {
 *   @Provides @SingleIn(SomeScope::class) @RealService
 *   fun provideRealMyService(
 *     @SomeQualifier serviceCreator: ServiceCreator,
 *   ): MyService
 *
 *   @Provides
 *   fun provideMyService(
 *     @RealService realService: Provider<MyService>,
 *     fakeService: Provider<FakeMyService>,
 *     @FakeMode isFakeMode: Boolean,
 *   ): MyService
 *
 *   @Provides
 *   fun provideContributedServiceReplacement(dependency: Dependency): FakeMyService
 * }
 * ```
 *
 * Functions are added directly to the class's declarations list (rather than through
 * `getCallableNamesForClass`/`generateFunctions`) so Metro can see them when deciding what nested
 * classes to generate (e.g., ProvidesFactory).
 *
 * Function bodies are generated by [ContributesServiceIrExtension] in the IR phase.
 */
public class ContributesServiceFir(session: FirSession) :
  MetroFirDeclarationGenerationExtension(session) {

  private val isReleaseBuild: Boolean
    get() = session.squareMetroExtensionsConfig.isReleaseBuild

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(ContributesServiceIds.PREDICATE)
  }

  override fun getContributionHints(): List<ContributionHint> {
    return session.predicateBasedProvider
      .getSymbolsByPredicate(ContributesServiceIds.PREDICATE)
      .filterIsInstance<FirRegularClassSymbol>()
      .mapNotNull { classSymbol ->
        val scopeClassId =
          extractScopeClassId(
            classSymbol,
            ContributesServiceIds.CONTRIBUTES_SERVICE_CLASS_ID,
            session,
          ) ?: return@mapNotNull null
        val nestedContainerClassId =
          classSymbol.classId.createNestedClassId(ContributesServiceIds.NESTED_CONTAINER_NAME)
        ContributionHint(contributingClassId = nestedContainerClassId, scope = scopeClassId)
      }
  }

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext,
  ): Set<Name> {
    if (hasAnnotation(classSymbol, ContributesServiceIds.CONTRIBUTES_SERVICE_CLASS_ID, session)) {
      return setOf(ContributesServiceIds.NESTED_CONTAINER_NAME)
    }
    return emptySet()
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): FirClassLikeSymbol<*>? {
    if (name != ContributesServiceIds.NESTED_CONTAINER_NAME) return null
    if (!hasAnnotation(owner, ContributesServiceIds.CONTRIBUTES_SERVICE_CLASS_ID, session))
      return null
    val ownerSymbol = owner as? FirRegularClassSymbol ?: return null
    val scopeArg =
      extractScopeArgument(owner, ContributesServiceIds.CONTRIBUTES_SERVICE_CLASS_ID, session)
        ?: return null

    // Check if this is a fake service (has replaces)
    val replacesClassIds = extractReplacesClassIds(owner)

    val nestedClassId = owner.classId.createNestedClassId(name)
    val classSymbol = FirRegularClassSymbol(nestedClassId)

    // Build the @Provides function(s) and add directly to the class declarations.
    // This makes them visible to Metro's getNestedClassifiersNames (which checks for @Provides
    // functions to decide whether to generate ProvidesFactory).
    val providesFunctions =
      if (replacesClassIds.isNotEmpty()) {
        val serviceFunctions = replacesClassIds.flatMap { replacedClassId ->
          buildFakeServiceFunctions(nestedClassId, ownerSymbol, scopeArg, replacedClassId)
        }
        val fakeServiceProviderFunction =
          if (hasInjectAnnotation(ownerSymbol)) {
            null
          } else {
            buildFakeServiceProvidesFunction(nestedClassId, ownerSymbol)
          }
        serviceFunctions + listOfNotNull(fakeServiceProviderFunction)
      } else {
        val qualifierClassId = findQualifierClassId(owner)
        listOf(buildRealServiceProvidesFunction(nestedClassId, owner, scopeArg, qualifierClassId))
      }

    val klass = buildRegularClass {
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = ContributesServiceGeneratorKey.origin
      source = owner.source
      classKind = ClassKind.OBJECT
      scopeProvider = session.kotlinScopeProvider
      this.name = nestedClassId.shortClassName
      symbol = classSymbol
      status =
        FirResolvedDeclarationStatusImpl(
          Visibilities.Public,
          Modality.FINAL,
          Visibilities.Public.toEffectiveVisibility(owner, forClass = true),
        )
      superTypeRefs += session.builtinTypes.anyType
      if (replacesClassIds.isNotEmpty()) {
        // For fake services, include replaces in @ContributesTo so Metro handles replacement
        // both in-compilation (FIR supertypes) and cross-module (IR merger).
        annotations += buildContributesToWithReplaces(scopeArg, replacesClassIds, owner)
      } else {
        annotations +=
          buildAnnotationCallWithScope(
            ClassIds.CONTRIBUTES_TO,
            ArgNames.SCOPE,
            scopeArg,
            owner,
            session,
          )
      }
      annotations += buildSimpleAnnotationCall(ClassIds.BINDING_CONTAINER, classSymbol)
      annotations +=
        buildAnnotationCallWithScope(
          ClassIds.ORIGIN,
          ArgNames.VALUE,
          buildClassExpression(owner, session),
          classSymbol,
          session,
        )
      // Add the function(s) directly to the class declarations
      for (fn in providesFunctions) {
        declarations += fn
      }
    }

    return klass.symbol
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    return if (isGeneratedContributionContainer(classSymbol)) {
      setOf(SpecialNames.INIT)
    } else {
      emptySet()
    }
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
    return if (isGeneratedContributionContainer(context.owner)) {
      listOf(createDefaultPrivateConstructor(context.owner, ContributesServiceGeneratorKey).symbol)
    } else {
      emptyList()
    }
  }

  /** Extract `replaces` ClassIds from the `@ContributesService` annotation. */
  private fun extractReplacesClassIds(owner: FirClassSymbol<*>): List<ClassId> {
    val annotation =
      findAnnotation(owner, ContributesServiceIds.CONTRIBUTES_SERVICE_CLASS_ID, session)
        ?: return emptyList()
    return extractClassIdsFromArrayArg(
      annotation,
      ArgNames.REPLACES,
      session,
      fallbackPackage = owner.classId.packageFqName,
      ownerSymbol = owner,
    )
  }

  /**
   * Build the single `@Provides` function for a real service (no `replaces`).
   *
   * Generates: `@Provides @SingleIn(scope) fun provideXxx(@Qualifier serviceCreator, @FakeMode
   * isFakeMode): ServiceType`
   */
  private fun buildRealServiceProvidesFunction(
    classId: ClassId,
    outerOwner: FirClassSymbol<*>,
    scopeArg: FirExpression,
    qualifierClassId: ClassId?,
  ): FirFunction {
    val outerClassId = outerOwner.classId
    val functionName = "provide${outerClassId.shortClassName.identifier}"
    val callableId = CallableId(classId, Name.identifier(functionName))

    val outerClassType = outerOwner.defaultType()
    val serviceCreatorType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(ClassIds.SERVICE_CREATOR),
        emptyArray(),
        isMarkedNullable = false,
      )
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
      origin = ContributesServiceGeneratorKey.origin
      symbol = functionSymbol
      name = callableId.callableName
      returnTypeRef = outerClassType.toFirResolvedTypeRef()
      dispatchReceiverType = dispatchType
      status =
        FirResolvedDeclarationStatusImpl(
          Visibilities.Public,
          Modality.FINAL,
          Visibilities.Public.toEffectiveVisibility(outerOwner, forClass = true),
        )

      // Parameter: @Qualifier serviceCreator: ServiceCreator
      this.valueParameters += buildValueParameter {
        resolvePhase = FirResolvePhase.BODY_RESOLVE
        moduleData = session.moduleData
        origin = ContributesServiceGeneratorKey.origin
        returnTypeRef = serviceCreatorType.toFirResolvedTypeRef()
        this.name = Name.identifier("serviceCreator")
        symbol = FirValueParameterSymbol()
        containingDeclarationSymbol = functionSymbol
        if (qualifierClassId != null) {
          annotations += buildSimpleAnnotation(qualifierClassId)
        }
      }

      // Parameter: @FakeMode isFakeMode: Boolean (only for non-release builds)
      if (!isReleaseBuild) {
        this.valueParameters += buildValueParameter {
          resolvePhase = FirResolvePhase.BODY_RESOLVE
          moduleData = session.moduleData
          origin = ContributesServiceGeneratorKey.origin
          returnTypeRef = session.builtinTypes.booleanType
          this.name = Name.identifier("isFakeMode")
          symbol = FirValueParameterSymbol()
          containingDeclarationSymbol = functionSymbol
          annotations += buildSimpleAnnotation(ClassIds.FAKE_MODE)
        }
      }

      annotations += buildSimpleAnnotationCall(ClassIds.PROVIDES, functionSymbol)
      annotations +=
        buildAnnotationCallWithScope(
          ClassIds.SINGLE_IN,
          ArgNames.SCOPE,
          scopeArg,
          functionSymbol,
          session,
        )
    }
  }

  /**
   * Build two `@Provides` functions for a fake service (has `replaces`).
   * 1. Real service provider under `@RealService` qualifier:
   *    `@Provides @SingleIn(scope) @RealService fun provideRealXxx(@Qualifier serviceCreator):
   *    ReplacedType`
   * 2. Switcher that picks real or fake based on `@FakeMode`: `@Provides fun
   *    provideXxx(@RealService real: Provider<ReplacedType>, fake: Provider<FakeType>,
   *
   *     @FakeMode isFakeMode: Boolean): ReplacedType`
   */
  private fun buildFakeServiceFunctions(
    nestedClassId: ClassId,
    fakeOwner: FirRegularClassSymbol,
    scopeArg: FirExpression,
    replacedClassId: ClassId,
  ): List<FirFunction> {
    val replacedSymbol =
      session.symbolProvider.getClassLikeSymbolByClassId(replacedClassId) as? FirClassSymbol<*>
        ?: return emptyList()
    val qualifierClassId = findQualifierClassId(replacedSymbol)

    val replacedType = replacedSymbol.defaultType()
    val fakeType = fakeOwner.defaultType()
    val replacedProviderType = providerTypeOf(replacedType)
    val fakeProviderType = providerTypeOf(fakeType)
    val serviceCreatorType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(ClassIds.SERVICE_CREATOR),
        emptyArray(),
        isMarkedNullable = false,
      )

    val dispatchType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(nestedClassId),
        emptyArray(),
        isMarkedNullable = false,
      )

    val replacedName = replacedClassId.shortClassName.identifier

    // Function 1: @Provides @SingleIn(scope) @RealService
    val realFnName = "provideReal${replacedName}"
    val realCallableId = CallableId(nestedClassId, Name.identifier(realFnName))
    val realFnSymbol = FirNamedFunctionSymbol(realCallableId)

    val realFn = buildNamedFunction {
      isLocal = false
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = ContributesServiceGeneratorKey.origin
      symbol = realFnSymbol
      name = realCallableId.callableName
      returnTypeRef = replacedType.toFirResolvedTypeRef()
      dispatchReceiverType = dispatchType
      status =
        FirResolvedDeclarationStatusImpl(
          Visibilities.Public,
          Modality.FINAL,
          Visibilities.Public.toEffectiveVisibility(fakeOwner, forClass = true),
        )

      // Parameter: @Qualifier serviceCreator: ServiceCreator
      this.valueParameters += buildValueParameter {
        resolvePhase = FirResolvePhase.BODY_RESOLVE
        moduleData = session.moduleData
        origin = ContributesServiceGeneratorKey.origin
        returnTypeRef = serviceCreatorType.toFirResolvedTypeRef()
        this.name = Name.identifier("serviceCreator")
        symbol = FirValueParameterSymbol()
        containingDeclarationSymbol = realFnSymbol
        if (qualifierClassId != null) {
          annotations += buildSimpleAnnotation(qualifierClassId)
        }
      }

      annotations += buildSimpleAnnotationCall(ClassIds.PROVIDES, realFnSymbol)
      annotations +=
        buildAnnotationCallWithScope(
          ClassIds.SINGLE_IN,
          ArgNames.SCOPE,
          scopeArg,
          realFnSymbol,
          session,
        )
      annotations += buildSimpleAnnotationCall(ClassIds.REAL_SERVICE, realFnSymbol)
    }

    // Function 2: @Provides switcher
    val switcherFnName = "provide${replacedName}"
    val switcherCallableId = CallableId(nestedClassId, Name.identifier(switcherFnName))
    val switcherFnSymbol = FirNamedFunctionSymbol(switcherCallableId)

    val switcherFn = buildNamedFunction {
      isLocal = false
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = ContributesServiceGeneratorKey.origin
      symbol = switcherFnSymbol
      name = switcherCallableId.callableName
      returnTypeRef = replacedType.toFirResolvedTypeRef()
      dispatchReceiverType = dispatchType
      status =
        FirResolvedDeclarationStatusImpl(
          Visibilities.Public,
          Modality.FINAL,
          Visibilities.Public.toEffectiveVisibility(fakeOwner, forClass = true),
        )

      // Parameter: @RealService realService: Provider<ReplacedType>
      this.valueParameters += buildValueParameter {
        resolvePhase = FirResolvePhase.BODY_RESOLVE
        moduleData = session.moduleData
        origin = ContributesServiceGeneratorKey.origin
        returnTypeRef = replacedProviderType.toFirResolvedTypeRef()
        this.name = Name.identifier("realService")
        symbol = FirValueParameterSymbol()
        containingDeclarationSymbol = switcherFnSymbol
        annotations += buildSimpleAnnotation(ClassIds.REAL_SERVICE)
      }

      // Parameter: fakeService: Provider<FakeMyService>
      this.valueParameters += buildValueParameter {
        resolvePhase = FirResolvePhase.BODY_RESOLVE
        moduleData = session.moduleData
        origin = ContributesServiceGeneratorKey.origin
        returnTypeRef = fakeProviderType.toFirResolvedTypeRef()
        this.name = Name.identifier("fakeService")
        symbol = FirValueParameterSymbol()
        containingDeclarationSymbol = switcherFnSymbol
      }

      // Parameter: @FakeMode isFakeMode: Boolean
      this.valueParameters += buildValueParameter {
        resolvePhase = FirResolvePhase.BODY_RESOLVE
        moduleData = session.moduleData
        origin = ContributesServiceGeneratorKey.origin
        returnTypeRef = session.builtinTypes.booleanType
        this.name = Name.identifier("isFakeMode")
        symbol = FirValueParameterSymbol()
        containingDeclarationSymbol = switcherFnSymbol
        annotations += buildSimpleAnnotation(ClassIds.FAKE_MODE)
      }

      annotations += buildSimpleAnnotationCall(ClassIds.PROVIDES, switcherFnSymbol)
    }

    return listOf(realFn, switcherFn)
  }

  /**
   * Build the `@Provides` function that constructs a fake service when it is not already
   * injectable.
   *
   * Generates: `@Provides fun provideContributedServiceReplacement(dependency: Dependency):
   * FakeService`
   */
  @OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
  private fun buildFakeServiceProvidesFunction(
    classId: ClassId,
    fakeOwner: FirRegularClassSymbol,
  ): FirFunction? {
    val callableId = CallableId(classId, Name.identifier(FAKE_SERVICE_PROVIDER_FUNCTION_NAME))
    val constructorSymbol =
      fakeOwner.declarationSymbols.filterIsInstance<FirConstructorSymbol>().firstOrNull()
        ?: return null

    val fakeType = fakeOwner.defaultType()
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
      origin = ContributesServiceGeneratorKey.origin
      symbol = functionSymbol
      name = callableId.callableName
      returnTypeRef = fakeType.toFirResolvedTypeRef()
      dispatchReceiverType = dispatchType
      status =
        FirResolvedDeclarationStatusImpl(
          Visibilities.Public,
          Modality.FINAL,
          Visibilities.Public.toEffectiveVisibility(fakeOwner, forClass = true),
        )

      for (parameter in constructorSymbol.fir.valueParameters) {
        valueParameters += buildValueParameter {
          resolvePhase = FirResolvePhase.BODY_RESOLVE
          moduleData = session.moduleData
          origin = ContributesServiceGeneratorKey.origin
          returnTypeRef = resolveParameterTypeRef(parameter, fakeOwner)
          this.name = parameter.name
          symbol = FirValueParameterSymbol()
          containingDeclarationSymbol = functionSymbol
          annotations += parameter.annotations
        }
      }

      annotations += buildSimpleAnnotationCall(ClassIds.PROVIDES, functionSymbol)
    }
  }

  private fun providerTypeOf(providedType: ConeKotlinType) =
    ConeClassLikeTypeImpl(
      ConeClassLikeLookupTagImpl(ClassIds.PROVIDER),
      arrayOf(providedType),
      isMarkedNullable = false,
    )

  /**
   * Build `@ContributesTo(scope, replaces =
   * [ReplacedClass::class, ReplacedClass.ServiceContribution::class])` as a [FirAnnotationCall]
   * with [buildResolvedArgumentList] so the FIR-to-IR converter properly serializes both the scope
   * and replaces arguments. This enables Metro's IR merger to read the replaces argument for
   * cross-module replacement.
   */
  @OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
  private fun buildContributesToWithReplaces(
    scopeArg: FirExpression,
    replacedClassIds: List<ClassId>,
    owner: FirClassSymbol<*>,
  ): FirAnnotationCall {
    val kClassClassId = ClassId(FqName("kotlin.reflect"), Name.identifier("KClass"))
    val replacedContributionClassIds = replacedClassIds.flatMap {
      listOf(it, it.createNestedClassId(ContributesServiceIds.NESTED_CONTAINER_NAME))
    }

    // Build resolved class references for the raw and generated service contribution classes.
    val getClassCalls = replacedContributionClassIds.map { replacedId ->
      val replacedType =
        ConeClassLikeTypeImpl(
          ConeClassLikeLookupTagImpl(replacedId),
          emptyArray(),
          isMarkedNullable = false,
        )
      val kClassType =
        ConeClassLikeTypeImpl(
          ConeClassLikeLookupTagImpl(kClassClassId),
          arrayOf(replacedType),
          isMarkedNullable = false,
        )
      val resolvedSymbol = session.symbolProvider.getClassLikeSymbolByClassId(replacedId)
      buildGetClassCall {
        coneTypeOrNull = kClassType
        argumentList = buildArgumentList {
          arguments += buildResolvedQualifier {
            packageFqName = replacedId.packageFqName
            relativeClassFqName = replacedId.relativeClassName
            coneTypeOrNull = replacedType
            symbol = resolvedSymbol
            resolvedToCompanionObject = false
          }
        }
      }
    }

    val arrayLiteral = buildCollectionLiteral {
      coneTypeOrNull = session.builtinTypes.anyType.coneType
      argumentList = buildArgumentList {
        for (call in getClassCalls) {
          arguments += call
        }
      }
    }

    val annotationType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(ClassIds.CONTRIBUTES_TO),
        emptyArray(),
        isMarkedNullable = false,
      )
    val annotationClassSymbol =
      session.symbolProvider.getClassLikeSymbolByClassId(ClassIds.CONTRIBUTES_TO)!!
    val constructorSymbol =
      (annotationClassSymbol as FirClassSymbol<*>)
        .declarationSymbols
        .filterIsInstance<FirConstructorSymbol>()
        .first()
    val scopeParam = constructorSymbol.fir.valueParameters.first { it.name == ArgNames.SCOPE }
    val replacesParam = constructorSymbol.fir.valueParameters.first { it.name == ArgNames.REPLACES }

    return buildAnnotationCall {
      annotationTypeRef = annotationType.toFirResolvedTypeRef()
      argumentMapping = buildAnnotationArgumentMapping {
        mapping[ArgNames.SCOPE] = scopeArg
        mapping[ArgNames.REPLACES] = arrayLiteral
      }
      argumentList =
        buildResolvedArgumentList(
          original = null,
          mapping = linkedMapOf(scopeArg to scopeParam, arrayLiteral to replacesParam),
        )
      calleeReference = buildResolvedNamedReference {
        name = ClassIds.CONTRIBUTES_TO.shortClassName
        resolvedSymbol = constructorSymbol
      }
      containingDeclarationSymbol = owner
      annotationResolvePhase = FirAnnotationResolvePhase.Types
    }
  }

  /** Find the qualifier annotation ClassId on a class (e.g., `@RetrofitAuthenticated`). */
  private fun findQualifierClassId(classSymbol: FirClassSymbol<*>): ClassId? {
    for (annotation in classSymbol.resolvedAnnotationsWithArguments) {
      val annotationClassId = annotation.toAnnotationClassIdSafe(session) ?: continue
      if (annotationClassId == ContributesServiceIds.CONTRIBUTES_SERVICE_CLASS_ID) continue
      val annotationSymbol =
        session.symbolProvider.getClassLikeSymbolByClassId(annotationClassId) as? FirClassSymbol<*>
          ?: continue
      val isQualifier =
        annotationSymbol.resolvedCompilerAnnotationsWithClassIds.any {
          it.toAnnotationClassIdSafe(session) in ClassIds.QUALIFIER_CLASS_IDS
        }
      if (isQualifier) return annotationClassId
    }
    return null
  }

  private fun isGeneratedContributionContainer(classSymbol: FirClassSymbol<*>): Boolean {
    return classSymbol.origin == ContributesServiceGeneratorKey.origin &&
      classSymbol.name == ContributesServiceIds.NESTED_CONTAINER_NAME
  }

  @OptIn(DirectDeclarationsAccess::class)
  private fun hasInjectAnnotation(serviceSymbol: FirRegularClassSymbol): Boolean {
    return hasAnnotation(serviceSymbol, ClassIds.INJECT, session) ||
      serviceSymbol.declarationSymbols.filterIsInstance<FirConstructorSymbol>().any {
        it.resolvedCompilerAnnotationsWithClassIds.any { annotation ->
          annotation.toAnnotationClassIdSafe(session) == ClassIds.INJECT
        }
      }
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

  private fun buildSimpleAnnotation(classId: ClassId): FirAnnotation {
    val annotationType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(classId),
        emptyArray(),
        isMarkedNullable = false,
      )
    return buildAnnotation {
      annotationTypeRef = annotationType.toFirResolvedTypeRef()
      argumentMapping = buildAnnotationArgumentMapping()
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
    ): MetroFirDeclarationGenerationExtension = ContributesServiceFir(session)
  }
}
