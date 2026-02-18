package com.squareup.metro.extensions.service

import com.squareup.metro.extensions.ClassIds
import com.squareup.metro.extensions.Keys.ContributesServiceGeneratorKey
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * IR extension that generates method bodies for `@Provides` functions created by
 * [ContributesServiceFir].
 *
 * For real services, generates:
 * ```
 * if (isFakeMode) error("No fake service provided for MyService.")
 * return serviceCreator.create(MyService::class.java)
 * ```
 */
internal class ContributesServiceIrExtension : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    moduleFragment.transformChildrenVoid(ContributesServiceIrTransformer(pluginContext))
  }
}

private class ContributesServiceIrTransformer(private val pluginContext: IrPluginContext) :
  IrElementTransformerVoid() {

  override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
    val origin = declaration.origin
    if (
      origin !is IrDeclarationOrigin.GeneratedByPlugin ||
        origin.pluginKey != ContributesServiceGeneratorKey
    ) {
      return super.visitSimpleFunction(declaration)
    }

    // Only generate bodies for functions that don't already have one
    if (declaration.body != null) return super.visitSimpleFunction(declaration)

    // Only handle provide* functions (not other generated declarations)
    if (!declaration.name.asString().startsWith("provide")) {
      return super.visitSimpleFunction(declaration)
    }

    generateProvidesBody(declaration)
    return super.visitSimpleFunction(declaration)
  }

  private fun generateProvidesBody(declaration: IrSimpleFunction) {
    val serviceType = declaration.returnType
    val serviceClassSymbol =
      (serviceType as? IrSimpleType)?.classOrNull ?: return

    // In Kotlin 2.3's unified parameters model, parameters include dispatch receiver first.
    // Our function is on an interface, so: [dispatch, serviceCreator, isFakeMode]
    val allParams = declaration.parameters
    // Find value parameters (skip dispatch receiver)
    val serviceCreatorParam = allParams.first { it.name.asString() == "serviceCreator" }
    val isFakeModeParam = allParams.first { it.name.asString() == "isFakeMode" }

    // Reference kotlin.error() function
    val errorFun =
      pluginContext
        .referenceFunctions(CallableId(FqName("kotlin"), Name.identifier("error")))
        .first()

    // Reference ServiceCreator.create() function
    val serviceCreatorClassSymbol =
      pluginContext.referenceClass(ClassIds.SERVICE_CREATOR) ?: return
    val createFun =
      serviceCreatorClassSymbol.owner.declarations
        .filterIsInstance<IrSimpleFunction>()
        .singleOrNull { it.name.asString() == "create" } ?: return

    // Reference kotlin.jvm.java extension property getter
    val javaPropertySymbol =
      pluginContext
        .referenceProperties(CallableId(FqName("kotlin.jvm"), Name.identifier("java")))
        .firstOrNull() ?: return
    val javaGetter = javaPropertySymbol.owner.getter?.symbol ?: return

    val irBuilder =
      DeclarationIrBuilder(
        pluginContext,
        declaration.symbol,
        declaration.startOffset,
        declaration.endOffset,
      )

    declaration.body =
      irBuilder.irBlockBody {
        // if (isFakeMode) error("No fake service provided for MyService.")
        val serviceName = serviceClassSymbol.owner.name.asString()
        val errorCall =
          irCall(errorFun).apply {
            arguments[0] = irString("No fake service provided for $serviceName.")
          }
        +irIfThen(pluginContext.irBuiltIns.unitType, irGet(isFakeModeParam), errorCall)

        // return serviceCreator.create(MyService::class.java)
        val kClassType = pluginContext.irBuiltIns.kClassClass.typeWith(serviceType)
        val classRef =
          IrClassReferenceImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            kClassType,
            serviceClassSymbol,
            serviceClassSymbol.defaultType,
          )

        // kotlin.jvm.java getter: [extensionReceiver]
        val javaClassExpr =
          irCall(javaGetter).apply { arguments[0] = classRef }

        // ServiceCreator.create(service): [dispatchReceiver, valueArg]
        val createCall =
          irCall(createFun.symbol).apply {
            arguments[0] = irGet(serviceCreatorParam)
            typeArguments[0] = serviceType
            arguments[1] = javaClassExpr
          }

        +irReturn(createCall)
      }
  }
}
