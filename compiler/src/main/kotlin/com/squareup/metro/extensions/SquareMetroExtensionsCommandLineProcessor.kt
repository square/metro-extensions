package com.squareup.metro.extensions

import com.fueledbycaffeine.autoservice.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

@AutoService(CommandLineProcessor::class)
public class SquareMetroExtensionsCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String = "com.squareup.metro.extensions"
  override val pluginOptions: Collection<CliOption> = emptyList()

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration,
  ) {
    error("Unexpected config option: '${option.optionName}'")
  }
}
