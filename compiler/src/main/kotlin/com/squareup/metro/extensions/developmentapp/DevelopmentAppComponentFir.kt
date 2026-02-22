package com.squareup.metro.extensions.developmentapp

import com.fueledbycaffeine.autoservice.AutoService
import com.squareup.metro.extensions.ArgNames
import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.Keys.DevelopmentAppComponentGeneratorKey
import com.squareup.metro.extensions.fir.buildAnnotationCallWithScope
import com.squareup.metro.extensions.fir.buildFirArrayLiteral
import com.squareup.metro.extensions.fir.buildFirFunction
import com.squareup.metro.extensions.fir.findAnnotation
import com.squareup.metro.extensions.fir.hasAnnotation
import dev.zacsweers.metro.compiler.MetroOptions
import dev.zacsweers.metro.compiler.api.fir.MetroFirDeclarationGenerationExtension
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
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
import org.jetbrains.kotlin.fir.expressions.builder.buildGetClassCall
import org.jetbrains.kotlin.fir.expressions.builder.buildResolvedQualifier
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
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
    register(DevelopmentAppComponentIds.PREDICATE)
  }

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext,
  ): Set<Name> {
    // Case 1: The annotated class itself — generate MetroComponent
    if (hasAnnotation(classSymbol, ClassIds.DEVELOPMENT_APP_COMPONENT, session)) {
      return setOf(DevelopmentAppComponentIds.METRO_COMPONENT_NAME)
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
          ArgNames.VALUE,
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

    return buildFirFunction {
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
    val resolvableExcludes =
      excludeClassIds.filter { session.symbolProvider.getClassLikeSymbolByClassId(it) != null }

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

    val getClassCalls =
      classIds.mapNotNull { classId ->
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

    return buildFirArrayLiteral {
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
    ): MetroFirDeclarationGenerationExtension = DevelopmentAppComponentFir(session)
  }
}
