package com.squareup.metro.extensions.scoped

import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.Keys.ContributesMultibindingScopedGeneratorKey
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * IR extension that generates method bodies for constructor `@Provides` functions created by
 * [ContributesMultibindingScopedFir].
 */
@Suppress("DEPRECATION")
internal class ContributesMultibindingScopedIrExtension : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    moduleFragment.transformChildrenVoid(ContributesMultibindingScopedIrTransformer(pluginContext))
  }
}

@Suppress("DEPRECATION")
private class ContributesMultibindingScopedIrTransformer(
  private val pluginContext: IrPluginContext
) : IrElementTransformerVoid() {

  override fun visitConstructor(declaration: IrConstructor): IrStatement {
    val origin = declaration.origin
    if (origin is IrDeclarationOrigin.GeneratedByPlugin) {
      declaration.startOffset = UNDEFINED_OFFSET
      declaration.endOffset = UNDEFINED_OFFSET
      declaration.body?.transformChildrenVoid(GeneratedConstructorOffsetTransformer)
    }
    return super.visitConstructor(declaration)
  }

  override fun visitDelegatingConstructorCall(
    expression: IrDelegatingConstructorCall
  ): IrExpression {
    return super.visitDelegatingConstructorCall(expression)
  }

  override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
    val origin = declaration.origin
    val generatedByPlugin = origin is IrDeclarationOrigin.GeneratedByPlugin
    if (
      generatedByPlugin &&
        origin.pluginKey == ContributesMultibindingScopedGeneratorKey &&
        declaration.body == null &&
        declaration.name.asString().startsWith(SCOPED_PROVIDER_FUNCTION_PREFIX)
    ) {
      generateProvidesBody(declaration)
    }

    val result = super.visitSimpleFunction(declaration)
    if (generatedByPlugin || hasGeneratedParent(declaration)) {
      declaration.startOffset = UNDEFINED_OFFSET
      declaration.endOffset = UNDEFINED_OFFSET
      declaration.body?.transformChildrenVoid(GeneratedBodyOffsetTransformer)
    }
    return result
  }

  private fun hasGeneratedParent(declaration: IrDeclaration): Boolean {
    var parent = declaration.parent
    while (parent is IrDeclaration) {
      if (parent.origin is IrDeclarationOrigin.GeneratedByPlugin) return true
      parent = parent.parent
    }
    return false
  }

  private fun generateProvidesBody(declaration: IrSimpleFunction) {
    val scopedClass =
      declaration.returnType.classOrNull?.owner?.takeIf { clazz ->
        clazz.declarations.any { it is IrConstructor }
      } ?: originClass(declaration) ?: return
    val constructor =
      scopedClass.declarations.filterIsInstance<IrConstructor>().firstOrNull() ?: return

    val irBuilder =
      DeclarationIrBuilder(pluginContext, declaration.symbol, UNDEFINED_OFFSET, UNDEFINED_OFFSET)

    val constructorParameters = constructor.parameters
    val providerParameters = constructorParameters.map { constructorParameter ->
      declaration.parameters.firstOrNull { it.name == constructorParameter.name } ?: return
    }

    val constructorCall =
      irBuilder.irCall(constructor.symbol, type = declaration.returnType).apply {
        for ((index, parameter) in providerParameters.withIndex()) {
          arguments[index] = irBuilder.irGet(parameter)
        }
      }

    declaration.body = irBuilder.irBlockBody { +irReturn(constructorCall) }
    declaration.startOffset = UNDEFINED_OFFSET
    declaration.endOffset = UNDEFINED_OFFSET
    declaration.body?.transformChildrenVoid(GeneratedBodyOffsetTransformer)
  }

  private fun originClass(declaration: IrDeclaration): IrClass? {
    var parent = declaration.parent
    while (parent is IrClass) {
      val originAnnotation =
        parent.annotations.firstOrNull { annotation ->
          annotation.symbol.owner.parentAsClass.classId == ClassIds.ORIGIN
        }
      val originClass =
        originAnnotation
          ?.arguments
          ?.firstOrNull()
          ?.let { it as? IrClassReference }
          ?.classType
          ?.classOrNull
          ?.owner
      if (originClass != null) return originClass
      parent = parent.parent
    }
    return null
  }

  private companion object {
    const val SCOPED_PROVIDER_FUNCTION_PREFIX = "provide"

    val GeneratedConstructorOffsetTransformer =
      object : IrElementTransformerVoid() {
        override fun visitDelegatingConstructorCall(
          expression: IrDelegatingConstructorCall
        ): IrExpression {
          expression.startOffset = UNDEFINED_OFFSET
          expression.endOffset = UNDEFINED_OFFSET
          return super.visitDelegatingConstructorCall(expression)
        }
      }

    val GeneratedBodyOffsetTransformer =
      object : IrElementTransformerVoid() {
        override fun visitExpression(expression: IrExpression): IrExpression {
          expression.startOffset = UNDEFINED_OFFSET
          expression.endOffset = UNDEFINED_OFFSET
          return super.visitExpression(expression)
        }
      }
  }
}
