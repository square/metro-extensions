package com.squareup.metro.extensions.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.resolve.typeResolver
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Build a simple [FirAnnotation] with a scope argument. */
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
 * Checks whether [classSymbol] has the given annotation. Uses
 * `resolvedCompilerAnnotationsWithClassIds` instead of `resolvedAnnotationClassIds` because the
 * latter forces lazy resolution to the TYPES phase, which can fail when called during the earlier
 * SUPERTYPES phase.
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

/** Finds the annotation with full argument resolution. */
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
 * Extracts the first argument (the scope) from the annotation on [classSymbol]. At the SUPERTYPES
 * stage, `argumentMapping` is not yet populated, so this uses the raw `argumentList` from the
 * [FirAnnotationCall].
 */
internal fun extractScopeArgument(
  classSymbol: FirClassSymbol<*>,
  annotationClassId: ClassId,
  session: FirSession,
): FirExpression? {
  val annotation = findAnnotation(classSymbol, annotationClassId, session) ?: return null
  val annotationCall = annotation as? FirAnnotationCall ?: return null
  return annotationCall.argumentList.arguments.firstOrNull()
}

/**
 * Extracts the scope [ClassId] from an annotation like `@SomeAnnotation(SomeScope::class)`.
 *
 * At the COMPILER_REQUIRED_ANNOTATIONS phase, annotation arguments may not be fully resolved. The
 * inner argument of `SomeScope::class` may be:
 * - [FirResolvedQualifier] (fully resolved) — extract classId directly
 * - [FirPropertyAccessExpression] (partially resolved) — extract from the resolved reference
 */
internal fun extractScopeClassId(
  classSymbol: FirRegularClassSymbol,
  annotationClassId: ClassId,
  session: FirSession,
): ClassId? {
  val annotation =
    classSymbol.resolvedCompilerAnnotationsWithClassIds.firstOrNull { ann ->
      ann.toAnnotationClassIdSafe(session) == annotationClassId
    } ?: return null

  val annotationCall = annotation as? FirAnnotationCall ?: return null
  val firstArg = annotationCall.argumentList.arguments.firstOrNull() ?: return null

  val getClassCall = firstArg as? FirGetClassCall ?: return null
  val innerArg = getClassCall.argumentList.arguments.firstOrNull() ?: return null

  return when (innerArg) {
    is FirResolvedQualifier -> innerArg.classId
    is FirPropertyAccessExpression -> {
      val ref = innerArg.calleeReference
      if (ref is FirResolvedNamedReference && ref.resolvedSymbol is FirClassLikeSymbol<*>) {
        (ref.resolvedSymbol as FirClassLikeSymbol<*>).classId
      } else {
        val name = ref.name
        session.symbolProvider.getClassLikeSymbolByClassId(ClassId(FqName("kotlin"), name))?.classId
      }
    }
    else -> null
  }
}

/**
 * Extracts [ClassId]s from a named array argument of a FIR annotation.
 *
 * Handles both resolved argument mappings and raw [FirAnnotationCall] argument lists. For example,
 * given `@MyAnnotation(replaces = [Foo::class, Bar::class])`, calling this with `argName = "replaces"`
 * returns `[ClassId(Foo), ClassId(Bar)]`.
 */
internal fun extractClassIdsFromArrayArg(
  annotation: FirAnnotation,
  argName: Name,
  session: FirSession,
  /** Package to try when resolving unresolved class references (e.g., for the FIR generation phase). */
  fallbackPackage: FqName? = null,
  /** Symbol of the class that has the annotation, used to find the file for import-scoped resolution. */
  ownerSymbol: FirClassLikeSymbol<*>? = null,
): List<ClassId> {
  // Try argument mapping first (resolved annotations)
  annotation.argumentMapping.mapping[argName]?.let { expr ->
    return extractClassIdsFromArrayExpression(expr, session, fallbackPackage, ownerSymbol)
  }

  // Fall back to FirAnnotationCall argument list (for partially resolved annotations)
  val annotationCall = annotation as? FirAnnotationCall ?: return emptyList()
  for (arg in annotationCall.argumentList.arguments) {
    val namedArg = arg as? FirNamedArgumentExpression ?: continue
    if (namedArg.name == argName) {
      return extractClassIdsFromArrayExpression(
        namedArg.expression, session, fallbackPackage, ownerSymbol,
      )
    }
  }
  return emptyList()
}

private fun extractClassIdsFromArrayExpression(
  expr: FirExpression,
  session: FirSession,
  fallbackPackage: FqName?,
  ownerSymbol: FirClassLikeSymbol<*>?,
): List<ClassId> {
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
        // At the FIR generation phase, class references may not be fully resolved yet.
        // Reconstruct a FirUserTypeRef from the property access qualifier chain and resolve
        // it using the file's import scopes (following Metro's TypeResolverFactory pattern).
        resolveClassIdViaTypeResolver(getClassCall, session, ownerSymbol)
          ?: run {
            // Fall back to name-based lookup in well-known packages.
            val name = ref.name
            session.symbolProvider
              .getClassLikeSymbolByClassId(ClassId(FqName("kotlin"), name))
              ?.classId
              ?: fallbackPackage?.let {
                session.symbolProvider
                  .getClassLikeSymbolByClassId(ClassId(it, name))
                  ?.classId
              }
          }
      }
    }
    else -> null
  }
}

/**
 * Resolve a [FirGetClassCall] to a [ClassId] by reconstructing a [FirUserTypeRef] from the
 * expression's qualifier chain and resolving it using the session's type resolver.
 *
 * This follows the same pattern as Metro's `TypeResolverFactory` — it reconstructs a type ref
 * from unresolved property access expressions and uses `session.typeResolver` to resolve it.
 */
private fun resolveClassIdViaTypeResolver(
  getClassCall: FirGetClassCall,
  session: FirSession,
  ownerSymbol: FirClassLikeSymbol<*>?,
): ClassId? {
  val source = getClassCall.source ?: return null
  val argument = getClassCall.argumentList.arguments.firstOrNull() ?: return null

  // Reconstruct a FirUserTypeRef from the FirPropertyAccessExpression qualifier chain.
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

  // Find the containing file to create file-scoped import resolution (following Metro's
  // TypeResolverFactory pattern). This enables resolving cross-package class references
  // like `LibService` imported from `com.squareup.test.lib`.
  val file = ownerSymbol?.let { session.firProvider.getFirClassifierContainerFileIfAny(it) }

  val scopes = if (file != null) {
    org.jetbrains.kotlin.fir.scopes.createImportingScopes(
      file, session, org.jetbrains.kotlin.fir.resolve.ScopeSession(),
    )
  } else {
    emptyList()
  }

  return try {
    val configuration =
      org.jetbrains.kotlin.fir.resolve.TypeResolutionConfiguration(
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
        supertypeSupplier = org.jetbrains.kotlin.fir.resolve.SupertypeSupplier.Default,
        expandTypeAliases = false,
      )
      .type
      .toRegularClassSymbol(session)
      ?.classId
  } catch (_: Exception) {
    null
  }
}

/**
 * Checks whether [declaration] has a transitive supertype matching any of the given [targetIds].
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
