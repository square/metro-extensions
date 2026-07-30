package com.squareup.metro.extensions.robot

import com.fueledbycaffeine.autoservice.AutoService
import com.squareup.metro.extensions.ArgNames
import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.Keys.ContributesRobotGeneratorKey
import com.squareup.metro.extensions.fir.buildAnnotationCallWithScope
import com.squareup.metro.extensions.fir.buildClassExpression
import com.squareup.metro.extensions.fir.extractScopeArgument
import com.squareup.metro.extensions.fir.extractScopeClassId
import com.squareup.metro.extensions.fir.hasAnnotation
import com.squareup.metro.extensions.fir.resolveValueParameterTypeRef
import dev.zacsweers.metro.compiler.MetroOptions
import dev.zacsweers.metro.compiler.api.fir.MetroContributionHintExtension
import dev.zacsweers.metro.compiler.api.fir.MetroContributionHintExtension.ContributionHint
import dev.zacsweers.metro.compiler.api.fir.MetroFirDeclarationGenerationExtension
import dev.zacsweers.metro.compiler.compat.CompatContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildNamedFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClass
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirAnnotationResolvePhase
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCall
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
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
 * Generates a nested `RobotContribution` interface for classes annotated with `@ContributesRobot`.
 *
 * For a class like:
 * ```
 * @ContributesRobot(SomeScope::class)
 * class AbcRobot(dependency: Dependency) : ScreenRobot<AbcRobot>()
 * ```
 *
 * This generator produces:
 * ```
 * @ContributesTo(SomeScope::class)
 * interface RobotContribution {
 *   fun getcom_test_AbcRobotComponent(): AbcRobot
 *
 *   @Provides
 *   fun provideAbcRobotComponent(dependency: Dependency): AbcRobot
 * }
 * ```
 *
 * When the robot is not already injectable, the generated provider function uses the same
 * parameters as the constructor and the IR phase fills in a body that calls the constructor
 * directly.
 */
public class ContributesRobotFir(session: FirSession) :
  MetroFirDeclarationGenerationExtension(session), MetroContributionHintExtension {

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
    val robotSymbol = owner as? FirRegularClassSymbol ?: return null
    val scopeArg =
      extractScopeArgument(owner, ContributesRobotIds.CONTRIBUTES_ROBOT_CLASS_ID, session)
        ?: return null

    val nestedClassId = owner.classId.createNestedClassId(name)
    val classSymbol = FirRegularClassSymbol(nestedClassId)
    val providesFunction =
      if (hasInjectAnnotation(robotSymbol)) {
        null
      } else {
        buildProvidesFunction(nestedClassId, robotSymbol) ?: return null
      }

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
      annotations +=
        buildAnnotationCallWithScope(
          ClassIds.CONTRIBUTES_TO,
          ArgNames.SCOPE,
          scopeArg,
          owner,
          session,
        )
      annotations +=
        buildAnnotationCallWithScope(
          ClassIds.ORIGIN,
          ArgNames.VALUE,
          buildClassExpression(owner, session),
          classSymbol,
          session,
        )
      if (providesFunction != null) {
        declarations += providesFunction
      }
    }

    return klass.symbol
  }

  @OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
  private fun buildProvidesFunction(
    classId: ClassId,
    robotSymbol: FirRegularClassSymbol,
  ): FirFunction? {
    val callableId = CallableId(classId, robotProviderFunctionName(robotSymbol.classId))
    val constructorSymbol =
      robotSymbol.declarationSymbols.filterIsInstance<FirConstructorSymbol>().firstOrNull()
        ?: return null
    val robotType = robotSymbol.defaultType()
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
      origin = ContributesRobotGeneratorKey.origin
      symbol = functionSymbol
      name = callableId.callableName
      returnTypeRef = robotType.toFirResolvedTypeRef()
      dispatchReceiverType = dispatchType
      status =
        FirResolvedDeclarationStatusImpl(
          Visibilities.Public,
          Modality.OPEN,
          Visibilities.Public.toEffectiveVisibility(robotSymbol, forClass = true),
        )

      for (parameter in constructorSymbol.fir.valueParameters) {
        valueParameters += buildValueParameter {
          resolvePhase = FirResolvePhase.BODY_RESOLVE
          moduleData = session.moduleData
          origin = ContributesRobotGeneratorKey.origin
          returnTypeRef = resolveValueParameterTypeRef(parameter, robotSymbol, session)
          this.name = parameter.name
          symbol = FirValueParameterSymbol()
          containingDeclarationSymbol = functionSymbol
          annotations += parameter.annotations
        }
      }

      annotations += buildSimpleAnnotationCall(ClassIds.PROVIDES, functionSymbol)
      extractScopeArgument(robotSymbol, ClassIds.SINGLE_IN, session)?.let { scopeArg ->
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
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    if (!isGeneratedContributionInterface(classSymbol)) return emptySet()

    // The outer class is the @ContributesRobot-annotated class that owns this nested interface.
    val outerClassId = classSymbol.classId.outerClassId ?: return emptySet()
    // Build the accessor from the robot's fqcn so robots with the same simple class name from
    // different packages still generate unique accessor method names.
    return setOf(robotAccessorFunctionName(outerClassId))
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

    buildNamedFunction {
      isLocal = false
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
          extractScopeClassId(classSymbol, ContributesRobotIds.CONTRIBUTES_ROBOT_CLASS_ID, session)
            ?: return@mapNotNull null
        val nestedInterfaceClassId =
          classSymbol.classId.createNestedClassId(ContributesRobotIds.NESTED_INTERFACE_NAME)
        ContributionHint(contributingClassId = nestedInterfaceClassId, scope = scopeClassId)
      }
  }

  private fun isGeneratedContributionInterface(classSymbol: FirClassSymbol<*>): Boolean {
    return classSymbol.origin == ContributesRobotGeneratorKey.origin &&
      classSymbol.name == ContributesRobotIds.NESTED_INTERFACE_NAME
  }

  @OptIn(DirectDeclarationsAccess::class)
  private fun hasInjectAnnotation(robotSymbol: FirRegularClassSymbol): Boolean {
    return hasAnnotation(robotSymbol, ClassIds.INJECT, session) ||
      robotSymbol.declarationSymbols.filterIsInstance<FirConstructorSymbol>().any {
        it.resolvedCompilerAnnotationsWithClassIds.any { annotation ->
          annotation.toAnnotationClassIdSafe(session) == ClassIds.INJECT
        }
      }
  }

  private fun robotAccessorFunctionName(contributorClassId: ClassId): Name {
    return Name.identifier(fqcnBasedAccessorName(contributorClassId))
  }

  private fun robotProviderFunctionName(contributorClassId: ClassId): Name {
    val fileName = contributorClassId.relativeClassName.asString().replace('.', '_') + "Component"
    return Name.identifier("provide$fileName")
  }

  private fun fqcnBasedAccessorName(contributorClassId: ClassId): String {
    val packageName = contributorClassId.packageFqName.asString()
    val generatedPackage =
      if (packageName.isEmpty()) {
        ""
      } else {
        "${packageName.replace('.', '_')}_"
      }
    val fileName = contributorClassId.relativeClassName.asString().replace('.', '_') + "Component"
    return "get$generatedPackage$fileName"
  }

  /**
   * Build an annotation as [FirAnnotationCall] so Metro recognizes it. Metro's `metroAnnotations()`
   * checks `annotation !is FirAnnotationCall` and skips plain annotations.
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
    ): MetroFirDeclarationGenerationExtension = ContributesRobotFir(session)
  }
}
