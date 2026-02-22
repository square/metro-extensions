package com.squareup.metro.extensions.featureflag

import com.squareup.metro.extensions.Keys.ContributesFeatureFlagGeneratorKey
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * IR extension that generates method bodies for `@Provides @IntoSet` functions created by
 * [ContributesFeatureFlagFir].
 *
 * The FIR phase generates function stubs (signatures without bodies). This extension fills in the
 * body during the IR phase: `return MyFlag` (the object instance).
 *
 * The function is identified by its origin key ([ContributesFeatureFlagGeneratorKey]) and name
 * prefix `provides`.
 */
internal class ContributesFeatureFlagIrExtension : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    moduleFragment.transformChildrenVoid(ContributesFeatureFlagIrTransformer(pluginContext))
  }
}

private class ContributesFeatureFlagIrTransformer(private val pluginContext: IrPluginContext) :
  IrElementTransformerVoid() {

  override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
    val origin = declaration.origin
    if (
      origin !is IrDeclarationOrigin.GeneratedByPlugin ||
        origin.pluginKey != ContributesFeatureFlagGeneratorKey
    ) {
      return super.visitSimpleFunction(declaration)
    }
    if (declaration.body != null) return super.visitSimpleFunction(declaration)
    if (!declaration.name.asString().startsWith("provides")) {
      return super.visitSimpleFunction(declaration)
    }

    generateProvidesBody(declaration)

    return super.visitSimpleFunction(declaration)
  }

  /**
   * Generates: `return MyFlag`
   *
   * The generated `FeatureFlagContribution` interface is nested inside the annotated object. We
   * navigate up the IR tree: function → FeatureFlagContribution → annotated object, then emit
   * `irGetObject` to reference the singleton instance.
   */
  private fun generateProvidesBody(declaration: IrSimpleFunction) {
    // function → FeatureFlagContribution interface → annotated object
    val contributionInterface = declaration.parent as? IrClass ?: return
    val flagObjectClass = contributionInterface.parent as? IrClass ?: return
    if (flagObjectClass.kind != ClassKind.OBJECT) return

    val irBuilder =
      DeclarationIrBuilder(
        pluginContext,
        declaration.symbol,
        declaration.startOffset,
        declaration.endOffset,
      )

    declaration.body = irBuilder.irBlockBody { +irReturn(irGetObject(flagObjectClass.symbol)) }
  }
}
