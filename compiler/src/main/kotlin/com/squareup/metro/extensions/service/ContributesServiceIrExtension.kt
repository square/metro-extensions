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
 * Handles two function patterns:
 *
 * 1. **Real service** (has `serviceCreator` + `isFakeMode`):
 *    ```
 *    if (isFakeMode) error("No fake service provided for MyService.")
 *    return serviceCreator.create(MyService::class.java)
 *    ```
 *
 * 2. **Fake service binding** (has `fakeService`):
 *    ```
 *    return fakeService
 *    ```
 */
@Suppress("DEPRECATION")
internal class ContributesServiceIrExtension : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    moduleFragment.transformChildrenVoid(ContributesServiceIrTransformer(pluginContext))
  }
}

@Suppress("DEPRECATION")
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

    val allParams = declaration.parameters

    // Detect function pattern by parameter names
    val hasFakeService = allParams.any { it.name.asString() == "fakeService" }
    val hasServiceCreator = allParams.any { it.name.asString() == "serviceCreator" }
    val hasIsFakeMode = allParams.any { it.name.asString() == "isFakeMode" }

    when {
      hasFakeService && !hasServiceCreator -> generateFakeBindingBody(declaration)
      hasServiceCreator && hasIsFakeMode -> generateRealServiceWithCheckBody(declaration)
    }

    return super.visitSimpleFunction(declaration)
  }

  /**
   * Real service with fake mode check (no replaces):
   * ```
   * if (isFakeMode) error("No fake service provided for MyService.")
   * return serviceCreator.create(MyService::class.java)
   * ```
   */
  private fun generateRealServiceWithCheckBody(declaration: IrSimpleFunction) {
    val serviceType = declaration.returnType
    val serviceClassSymbol =
      (serviceType as? IrSimpleType)?.classOrNull ?: return

    val allParams = declaration.parameters
    val serviceCreatorParam = allParams.first { it.name.asString() == "serviceCreator" }
    val isFakeModeParam = allParams.first { it.name.asString() == "isFakeMode" }

    val errorFun =
      pluginContext
        .referenceFunctions(CallableId(FqName("kotlin"), Name.identifier("error")))
        .first()

    val serviceCreatorClassSymbol =
      pluginContext.referenceClass(ClassIds.SERVICE_CREATOR) ?: return
    val createFun =
      serviceCreatorClassSymbol.owner.declarations
        .filterIsInstance<IrSimpleFunction>()
        .singleOrNull { it.name.asString() == "create" } ?: return

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
        val serviceName = serviceClassSymbol.owner.name.asString()
        val errorCall =
          irCall(errorFun).apply {
            arguments[0] = irString("No fake service provided for $serviceName.")
          }
        +irIfThen(pluginContext.irBuiltIns.unitType, irGet(isFakeModeParam), errorCall)

        val kClassType = pluginContext.irBuiltIns.kClassClass.typeWith(serviceType)
        val classRef =
          IrClassReferenceImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            kClassType,
            serviceClassSymbol,
            serviceClassSymbol.defaultType,
          )

        val javaClassExpr =
          irCall(javaGetter).apply { arguments[0] = classRef }

        val createCall =
          irCall(createFun.symbol).apply {
            arguments[0] = irGet(serviceCreatorParam)
            typeArguments[0] = serviceType
            arguments[1] = javaClassExpr
          }

        +irReturn(createCall)
      }
  }

  /**
   * Fake service binding: simply returns the fake service cast to the replaced type.
   * ```
   * return fakeService
   * ```
   */
  private fun generateFakeBindingBody(declaration: IrSimpleFunction) {
    val allParams = declaration.parameters
    val fakeServiceParam = allParams.first { it.name.asString() == "fakeService" }

    val irBuilder =
      DeclarationIrBuilder(
        pluginContext,
        declaration.symbol,
        declaration.startOffset,
        declaration.endOffset,
      )

    declaration.body =
      irBuilder.irBlockBody {
        +irReturn(irGet(fakeServiceParam))
      }
  }
}
