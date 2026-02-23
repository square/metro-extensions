package com.squareup.metro.extensions.fir

import com.squareup.metro.extensions.ArgNames
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirAnnotationResolvePhase
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.SupertypeSupplier
import org.jetbrains.kotlin.fir.resolve.TypeResolutionConfiguration
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.resolve.typeResolver
import org.jetbrains.kotlin.fir.scopes.createImportingScopes
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Build a simple [FirAnnotation] with a scope argument in its [argumentMapping]. */
internal fun buildAnnotationWithScope(
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

/**
 * Build an annotation with a scope argument as [FirAnnotationCall] so Metro recognizes it.
 *
 * Metro's `metroAnnotations()` checks `annotation !is FirAnnotationCall` and skips plain
 * [FirAnnotation] instances. Uses [buildResolvedArgumentList] so the FIR-to-IR converter recognizes
 * the arguments.
 */
@OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
internal fun buildAnnotationCallWithScope(
  classId: ClassId,
  argName: Name,
  scopeArg: FirExpression,
  containingSymbol: FirBasedSymbol<*>,
  session: FirSession,
): FirAnnotationCall {
  val annotationType =
    ConeClassLikeTypeImpl(
      ConeClassLikeLookupTagImpl(classId),
      emptyArray(),
      isMarkedNullable = false,
    )
  val annotationClassSymbol = session.symbolProvider.getClassLikeSymbolByClassId(classId)!!
  val constructorSymbol =
    (annotationClassSymbol as FirClassSymbol<*>)
      .declarationSymbols
      .filterIsInstance<FirConstructorSymbol>()
      .first()
  val scopeParam = constructorSymbol.fir.valueParameters.first { it.name == argName }

  return org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCall {
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

/**
 * Checks whether [classSymbol] has the given annotation.
 *
 * Uses `resolvedCompilerAnnotationsWithClassIds` (safe at early phases) instead of
 * `resolvedAnnotationClassIds` which forces lazy resolution to the TYPES phase and can fail during
 * SUPERTYPES.
 */
internal fun hasAnnotation(
  classSymbol: FirClassSymbol<*>,
  annotationClassId: ClassId,
  session: FirSession,
): Boolean {
  return classSymbol.resolvedCompilerAnnotationsWithClassIds.any {
    it.toAnnotationClassIdSafe(session) == annotationClassId
  }
}

/**
 * Finds the annotation on [classSymbol] with the given [annotationClassId], triggering argument
 * resolution via `resolvedAnnotationsWithArguments`.
 */
internal fun findAnnotation(
  classSymbol: FirClassSymbol<*>,
  annotationClassId: ClassId,
  session: FirSession,
): FirAnnotation? {
  return classSymbol.resolvedAnnotationsWithArguments.firstOrNull { annotation ->
    annotation.toAnnotationClassIdSafe(session) == annotationClassId
  }
}

/**
 * Extracts the first argument (the scope) from the annotation on [classSymbol].
 *
 * At the SUPERTYPES stage, `argumentMapping` is not yet populated, so this reads the raw
 * `argumentList` from the [FirAnnotationCall].
 *
 * Unwraps [FirNamedArgumentExpression] to return the underlying expression, since named arguments
 * (e.g., `scope = AppScope::class`) wrap the actual [FirGetClassCall] in a named wrapper that
 * downstream consumers (like Metro's `scopeArgument()`) don't expect.
 */
internal fun extractScopeArgument(
  classSymbol: FirClassSymbol<*>,
  annotationClassId: ClassId,
  session: FirSession,
): FirExpression? {
  val annotation = findAnnotation(classSymbol, annotationClassId, session) ?: return null
  val annotationCall = annotation as? FirAnnotationCall ?: return null
  val firstArg = annotationCall.argumentList.arguments.firstOrNull() ?: return null
  // Unwrap named arguments (e.g., `scope = AppScope::class`) to get the bare expression.
  return if (firstArg is FirNamedArgumentExpression) firstArg.expression else firstArg
}

/**
 * Extracts the scope [ClassId] from an annotation like `@SomeAnnotation(SomeScope::class)`.
 *
 * Uses `resolvedAnnotationsWithArguments` to get the annotation with argument resolution. Even
 * after resolution, the inner `::class` expression may contain an unresolved
 * [FirSimpleNamedReference][org.jetbrains.kotlin.fir.references.FirSimpleNamedReference] for
 * non-builtin scope classes (e.g., `AppScope`). In that case, the function scans the containing
 * file's explicit imports to resolve the class by simple name.
 */
internal fun extractScopeClassId(
  classSymbol: FirRegularClassSymbol,
  annotationClassId: ClassId,
  session: FirSession,
): ClassId? {
  val annotation = findAnnotation(classSymbol, annotationClassId, session) ?: return null
  val annotationCall = annotation as? FirAnnotationCall ?: return null

  // Try the resolved argument mapping first (populated after full resolution), then fall back
  // to the raw argument list.
  val scopeExpr =
    annotationCall.argumentMapping.mapping[ArgNames.SCOPE]
      ?: annotationCall.argumentList.arguments.firstOrNull()
      ?: return null

  val getClassCall = scopeExpr as? FirGetClassCall ?: return null
  val innerArg = getClassCall.argumentList.arguments.firstOrNull() ?: return null

  return when (innerArg) {
    is FirResolvedQualifier -> innerArg.classId
    is FirPropertyAccessExpression -> {
      val ref = innerArg.calleeReference
      if (ref is FirResolvedNamedReference && ref.resolvedSymbol is FirClassLikeSymbol<*>) {
        (ref.resolvedSymbol as FirClassLikeSymbol<*>).classId
      } else {
        // At the COMPILER_REQUIRED_ANNOTATIONS phase, references may not be fully resolved.
        // Scan the containing file's explicit imports for a matching simple name, then fall back
        // to the kotlin package for well-known types (Unit, Int, etc.).
        val name = ref.name
        val file = session.firProvider.getFirClassifierContainerFileIfAny(classSymbol)
        val importedClassId =
          file?.imports?.firstNotNullOfOrNull { import ->
            if (import.isAllUnder) return@firstNotNullOfOrNull null
            val importedFqName = import.importedFqName ?: return@firstNotNullOfOrNull null
            if (importedFqName.shortName() == name) {
              val classId = ClassId.topLevel(importedFqName)
              session.symbolProvider.getClassLikeSymbolByClassId(classId)?.classId
            } else {
              null
            }
          }
        importedClassId
          ?: session.symbolProvider
            .getClassLikeSymbolByClassId(ClassId(FqName("kotlin"), name))
            ?.classId
      }
    }
    else -> null
  }
}

/**
 * Extracts [ClassId]s from a named array argument of a FIR annotation.
 *
 * Handles both resolved argument mappings and raw [FirAnnotationCall] argument lists. For example,
 * given `@MyAnnotation(replaces = [Foo::class, Bar::class])`, calling this with `argName =
 * "replaces"` returns `[ClassId(Foo), ClassId(Bar)]`.
 *
 * @param ownerSymbol The class that has the annotation. Used to find the containing file for
 *   import-scoped type resolution of cross-package class references. Pass `null` when calling from
 *   a checker (where annotation arguments are already fully resolved).
 * @param fallbackPackage Package to try when the class reference can't be resolved otherwise
 *   (typically the annotated class's own package).
 */
internal fun extractClassIdsFromArrayArg(
  annotation: FirAnnotation,
  argName: Name,
  session: FirSession,
  fallbackPackage: FqName? = null,
  ownerSymbol: FirClassLikeSymbol<*>? = null,
): List<ClassId> {
  // Try argument mapping first (populated when annotation arguments are fully resolved).
  annotation.argumentMapping.mapping[argName]?.let { expr ->
    return extractClassIdsFromArrayExpression(expr, session, fallbackPackage, ownerSymbol)
  }

  // Fall back to raw FirAnnotationCall argument list (for partially resolved annotations
  // during FIR generation, where argumentMapping is not yet populated).
  val annotationCall = annotation as? FirAnnotationCall ?: return emptyList()
  for (arg in annotationCall.argumentList.arguments) {
    val namedArg = arg as? FirNamedArgumentExpression ?: continue
    if (namedArg.name == argName) {
      return extractClassIdsFromArrayExpression(
        namedArg.expression,
        session,
        fallbackPackage,
        ownerSymbol,
      )
    }
  }
  return emptyList()
}

/** Extract ClassIds from an array literal expression (e.g., `[Foo::class, Bar::class]`). */
private fun extractClassIdsFromArrayExpression(
  expr: FirExpression,
  session: FirSession,
  fallbackPackage: FqName?,
  ownerSymbol: FirClassLikeSymbol<*>?,
): List<ClassId> {
  // The array literal is a FirCall (FirArrayLiteral or FirFunctionCall for implicitArrayOf).
  val arrayElements =
    when (expr) {
      is FirCall -> expr.argumentList.arguments
      else -> return emptyList()
    }
  return arrayElements.mapNotNull { element ->
    if (element is FirGetClassCall) {
      resolveClassIdFromGetClassCall(element, session, fallbackPackage, ownerSymbol)
    } else {
      null
    }
  }
}

/**
 * Resolve a `Foo::class` expression ([FirGetClassCall]) to its [ClassId].
 *
 * Uses a multi-level resolution strategy:
 * 1. Already resolved: [FirResolvedQualifier] → extract classId directly
 * 2. Resolved reference: [FirResolvedNamedReference] → extract from symbol
 * 3. Type resolver: Reconstruct a [FirUserTypeRef][org.jetbrains.kotlin.fir.types.FirUserTypeRef]
 *    and resolve using the containing file's import scopes (following Metro's TypeResolverFactory)
 * 4. Name-based fallback: Try `kotlin` package and the [fallbackPackage]
 */
private fun resolveClassIdFromGetClassCall(
  getClassCall: FirGetClassCall,
  session: FirSession,
  fallbackPackage: FqName?,
  ownerSymbol: FirClassLikeSymbol<*>?,
): ClassId? {
  val innerArg = getClassCall.argumentList.arguments.firstOrNull() ?: return null
  return when (innerArg) {
    is FirResolvedQualifier -> innerArg.classId
    is FirPropertyAccessExpression -> {
      val ref = innerArg.calleeReference
      if (ref is FirResolvedNamedReference && ref.resolvedSymbol is FirClassLikeSymbol<*>) {
        (ref.resolvedSymbol as FirClassLikeSymbol<*>).classId
      } else {
        // At the FIR generation phase, class references in annotation array arguments may not
        // be fully resolved. Use the session's type resolver with file-scoped import context
        // to resolve cross-package references (e.g., `LibService` imported from another pkg).
        resolveClassIdViaTypeResolver(getClassCall, session, ownerSymbol)
          ?: run {
            // Last resort: try well-known packages by simple name.
            val name = ref.name
            session.symbolProvider
              .getClassLikeSymbolByClassId(ClassId(FqName("kotlin"), name))
              ?.classId
              ?: fallbackPackage?.let {
                session.symbolProvider.getClassLikeSymbolByClassId(ClassId(it, name))?.classId
              }
          }
      }
    }
    else -> null
  }
}

/**
 * Resolve a [FirGetClassCall] to a [ClassId] by reconstructing a
 * [FirUserTypeRef][org.jetbrains.kotlin.fir.types.FirUserTypeRef] from the expression's qualifier
 * chain and resolving it using the session's type resolver with the containing file's import
 * scopes.
 *
 * This follows Metro's `TypeResolverFactory` pattern:
 * 1. Walk the [FirPropertyAccessExpression] chain to extract qualifier name parts
 * 2. Build a synthetic [FirUserTypeRef][org.jetbrains.kotlin.fir.types.FirUserTypeRef]
 * 3. Find the containing file via `firProvider.getFirClassifierContainerFileIfAny()`
 * 4. Create importing scopes from the file
 * 5. Resolve the type ref using `session.typeResolver` with those scopes
 *
 * This enables resolving cross-package class references like `LibService` that are imported in the
 * source file but not yet resolved in the FIR tree during early compilation phases.
 */
private fun resolveClassIdViaTypeResolver(
  getClassCall: FirGetClassCall,
  session: FirSession,
  ownerSymbol: FirClassLikeSymbol<*>?,
): ClassId? {
  val source = getClassCall.source ?: return null
  val argument = getClassCall.argumentList.arguments.firstOrNull() ?: return null

  // Reconstruct a FirUserTypeRef from the FirPropertyAccessExpression qualifier chain.
  // For `com.example.LibService::class`, this walks `com` → `example` → `LibService`.
  // For a simple imported `LibService::class`, this produces a single-segment qualifier.
  val typeRef =
    org.jetbrains.kotlin.fir.types.builder.buildUserTypeRef {
      isMarkedNullable = false
      this.source = source
      org.jetbrains.kotlin.fir.extensions.QualifierPartBuilder(qualifier).apply {
        fun visitQualifiers(expression: FirExpression) {
          if (expression !is FirPropertyAccessExpression) return
          expression.explicitReceiver?.let { visitQualifiers(it) }
          part(expression.calleeReference.name)
        }
        visitQualifiers(argument)
      }
    }
  if (typeRef.qualifier.isEmpty()) return null

  // Find the file containing the annotated class to set up import-scoped resolution.
  val file = ownerSymbol?.let { session.firProvider.getFirClassifierContainerFileIfAny(it) }
  val scopes =
    if (file != null) {
      createImportingScopes(file, session, ScopeSession())
    } else {
      emptyList()
    }

  return try {
    val configuration =
      TypeResolutionConfiguration(
        scopes = scopes,
        containingClassDeclarations = emptyList(),
        useSiteFile = file,
      )
    session.typeResolver
      .resolveType(
        typeRef = typeRef,
        configuration = configuration,
        areBareTypesAllowed = true,
        isOperandOfIsOperator = false,
        resolveDeprecations = false,
        supertypeSupplier = SupertypeSupplier.Default,
        expandTypeAliases = false,
      )
      .type
      .toRegularClassSymbol(session)
      ?.classId
  } catch (_: Exception) {
    // Resolution can fail if the type isn't importable in this context.
    null
  }
}

/**
 * Checks whether [type] has a transitive supertype matching any of the given [targetIds].
 *
 * @param visited Tracks already-visited ClassIds to prevent infinite loops in diamond hierarchies.
 */
internal fun hasTransitiveSupertype(
  type: ConeKotlinType,
  session: FirSession,
  targetIds: Collection<ClassId>,
  visited: MutableSet<ClassId> = mutableSetOf(),
): Boolean {
  val classSymbol = type.toRegularClassSymbol(session) ?: return false
  val classId = classSymbol.classId
  if (!visited.add(classId)) return false
  if (classId in targetIds) return true

  return classSymbol.resolvedSuperTypeRefs.any { superTypeRef ->
    val superConeType = superTypeRef.coneType.fullyExpandedType(session)
    hasTransitiveSupertype(superConeType, session, targetIds, visited)
  }
}
