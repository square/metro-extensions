package com.squareup.metro.extensions.robot

import com.squareup.metro.extensions.Keys.ContributesRobotGeneratorKey
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * IR extension that generates method bodies for `@Provides` functions created by
 * [ContributesRobotFir].
 *
 * The FIR phase generates provider stubs whose parameters mirror the robot constructor. This fills
 * in the body as `return Robot(arg1, arg2, ...)`.
 */
@Suppress("DEPRECATION")
internal class ContributesRobotIrExtension : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    moduleFragment.transformChildrenVoid(ContributesRobotIrTransformer(pluginContext))
  }
}

@Suppress("DEPRECATION")
private class ContributesRobotIrTransformer(private val pluginContext: IrPluginContext) :
  IrElementTransformerVoid() {

  override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
    val origin = declaration.origin
    if (
      origin !is IrDeclarationOrigin.GeneratedByPlugin ||
        origin.pluginKey != ContributesRobotGeneratorKey
    ) {
      return super.visitSimpleFunction(declaration)
    }
    if (declaration.body != null) return super.visitSimpleFunction(declaration)
    if (!declaration.name.asString().startsWith("provide")) {
      return super.visitSimpleFunction(declaration)
    }

    generateProvidesBody(declaration)

    return super.visitSimpleFunction(declaration)
  }

  private fun generateProvidesBody(declaration: IrSimpleFunction) {
    val contributionInterface = declaration.parent as? IrClass ?: return
    val robotClass = contributionInterface.parent as? IrClass ?: return
    val constructor =
      robotClass.declarations.filterIsInstance<IrConstructor>().firstOrNull() ?: return

    val irBuilder =
      DeclarationIrBuilder(
        pluginContext,
        declaration.symbol,
        declaration.startOffset,
        declaration.endOffset,
      )

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
  }
}
