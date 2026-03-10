package com.squareup.metro.extensions

import com.fueledbycaffeine.autoservice.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

@AutoService(CommandLineProcessor::class)
public class SquareMetroExtensionsCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String = "com.squareup.metro.extensions"

  override val pluginOptions: Collection<CliOption> =
    listOf(
      CliOption(
        optionName = OPTION_IS_RELEASE_BUILD,
        valueDescription = "<true|false>",
        description = "Whether this is a release build",
        required = false,
      )
    )

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration,
  ) {
    when (option.optionName) {
      OPTION_IS_RELEASE_BUILD ->
        configuration.put(SquareMetroExtensionsConfig.IS_RELEASE_BUILD_KEY, value.toBooleanStrict())
      else -> error("Unexpected config option: '${option.optionName}'")
    }
  }

  internal companion object {
    const val OPTION_IS_RELEASE_BUILD = "isReleaseBuild"
  }
}
