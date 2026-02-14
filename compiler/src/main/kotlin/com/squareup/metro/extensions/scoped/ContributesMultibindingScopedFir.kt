package com.squareup.metro.extensions.scoped

import com.fueledbycaffeine.autoservice.AutoService
import com.squareup.metro.extensions.ArgNames
import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.Keys.ContributesMultibindingScopedGeneratorKey
import dev.zacsweers.metro.compiler.MetroOptions
import dev.zacsweers.metro.compiler.api.fir.MetroFirDeclarationGenerationExtension
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClass
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
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
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.moduleData
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
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * Generates a nested `MultibindingScopedContribution` interface for classes annotated with
 * `@ContributesMultibindingScoped`.
 *
 * For a class like:
 * ```
 * @Inject
 * @ContributesMultibindingScoped(SomeScope::class)
 * class MyService : Scoped
 * ```
 *
 * This generator produces:
 * ```
 * @ContributesTo(SomeScope::class)
 * interface MultibindingScopedContribution {
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
    if (hasAnnotation(classSymbol)) {
      return setOf(ContributesMultibindingScopedIds.NESTED_INTERFACE_NAME)
    }
    return emptySet()
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): FirClassLikeSymbol<*>? {
    if (name != ContributesMultibindingScopedIds.NESTED_INTERFACE_NAME) return null
    if (!hasAnnotation(owner)) return null
    val scopeArg = extractScopeArgument(owner) ?: return null

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
      annotations += buildAnnotationWithScope(ClassIds.CONTRIBUTES_TO, ArgNames.SCOPE, scopeArg)
      // Add the function directly to the class declarations
      declarations += bindsFunction
    }

    return klass.symbol
  }

  private fun hasAnnotation(classSymbol: FirClassSymbol<*>): Boolean {
    // Use resolvedCompilerAnnotationsWithClassIds instead of resolvedAnnotationClassIds
    // because the latter forces lazy resolution to TYPES phase, which can fail when
    // getNestedClassifiersNames is called during the earlier SUPERTYPES phase.
    return classSymbol.resolvedCompilerAnnotationsWithClassIds.any {
      it.toAnnotationClassIdSafe(session) ==
        ContributesMultibindingScopedIds.CONTRIBUTES_MULTIBINDING_SCOPED_CLASS_ID
    }
  }

  private fun findAnnotation(classSymbol: FirClassSymbol<*>): FirAnnotation? {
    return classSymbol.resolvedAnnotationsWithArguments.firstOrNull { annotation ->
      annotation.toAnnotationClassIdSafe(session) ==
        ContributesMultibindingScopedIds.CONTRIBUTES_MULTIBINDING_SCOPED_CLASS_ID
    }
  }

  private fun extractScopeArgument(classSymbol: FirClassSymbol<*>): FirExpression? {
    val annotation = findAnnotation(classSymbol) ?: return null
    // At SUPERTYPES stage, argumentMapping is not yet populated (happens at ANNOTATION_ARGUMENTS).
    // Use the raw argumentList from the FirAnnotationCall instead.
    val annotationCall = annotation as? FirAnnotationCall ?: return null
    return annotationCall.argumentList.arguments.firstOrNull()
  }

  private fun buildBindsFunction(
    classId: ClassId,
    outerOwner: FirClassSymbol<*>,
    scopeArg: FirExpression,
  ): FirSimpleFunction {
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

    return buildSimpleFunction {
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
        buildAnnotationCallWithScope(ClassIds.FOR_SCOPE, ArgNames.VALUE, scopeArg, functionSymbol)
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

  /**
   * Build an annotation with scope argument as [FirAnnotationCall].
   *
   * Uses [buildResolvedArgumentList] so the FIR-to-IR converter recognizes the arguments. The
   * converter checks `argumentList is FirResolvedArgumentList` to extract the argument mapping — a
   * plain `buildArgumentList` would be treated as unresolved.
   */
  @OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
  private fun buildAnnotationCallWithScope(
    classId: ClassId,
    argName: Name,
    scopeArg: FirExpression,
    containingSymbol: FirBasedSymbol<*>,
  ): FirAnnotationCall {
    val annotationType =
      ConeClassLikeTypeImpl(
        ConeClassLikeLookupTagImpl(classId),
        emptyArray(),
        isMarkedNullable = false,
      )

    // Look up the annotation constructor and its parameter
    val annotationClassSymbol = session.symbolProvider.getClassLikeSymbolByClassId(classId)!!
    val constructorSymbol =
      (annotationClassSymbol as FirClassSymbol<*>)
        .declarationSymbols
        .filterIsInstance<FirConstructorSymbol>()
        .first()
    val scopeParam = constructorSymbol.fir.valueParameters.first { it.name == argName }

    return buildAnnotationCall {
      annotationTypeRef = annotationType.toFirResolvedTypeRef()
      argumentMapping = buildAnnotationArgumentMapping { mapping[argName] = scopeArg }
      argumentList =
        buildResolvedArgumentList(original = null, mapping = linkedMapOf(scopeArg to scopeParam))
      calleeReference = buildResolvedNamedReference {
        name = classId.shortClassName
        resolvedSymbol = constructorSymbol
      }
      containingDeclarationSymbol = containingSymbol
      annotationResolvePhase = FirAnnotationResolvePhase.Types
    }
  }

  private fun buildAnnotationWithScope(
    classId: ClassId,
    argName: Name,
    scopeArg: FirExpression,
  ): FirAnnotation {
    return buildAnnotation {
      annotationTypeRef =
        ConeClassLikeTypeImpl(
            ConeClassLikeLookupTagImpl(classId),
            emptyArray(),
            isMarkedNullable = false,
          )
          .toFirResolvedTypeRef()
      argumentMapping = buildAnnotationArgumentMapping { mapping[argName] = scopeArg }
    }
  }

  @AutoService(MetroFirDeclarationGenerationExtension.Factory::class)
  public class Factory : MetroFirDeclarationGenerationExtension.Factory {
    override fun create(
      session: FirSession,
      options: MetroOptions,
    ): MetroFirDeclarationGenerationExtension = ContributesMultibindingScopedFir(session)
  }
}
