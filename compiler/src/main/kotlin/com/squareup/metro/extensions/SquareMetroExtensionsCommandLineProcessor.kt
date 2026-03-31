package com.squareup.metro.extensions

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

public class SquareMetroExtensionsCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String = "com.squareup.metro.extensions"

  override val pluginOptions: Collection<CliOption> =
    listOf(
      CliOption(
        optionName = OPTION_IS_RELEASE_BUILD,
        valueDescription = "<true|false>",
        description = "Whether this is a release build",
        required = false,
      ),
      CliOption(
        optionName = OPTION_ENABLE_DEVELOPMENT_APP_COMPONENT,
        valueDescription = "<true|false>",
        description = "Whether the @DevelopmentAppComponent FIR extension is enabled",
        required = false,
      ),
    )

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration,
  ) {
    when (option.optionName) {
      OPTION_IS_RELEASE_BUILD ->
        configuration.put(SquareMetroExtensionsConfig.IS_RELEASE_BUILD_KEY, value.toBooleanStrict())
      OPTION_ENABLE_DEVELOPMENT_APP_COMPONENT ->
        configuration.put(
          SquareMetroExtensionsConfig.ENABLE_DEVELOPMENT_APP_COMPONENT_KEY,
          value.toBooleanStrict(),
        )
      else -> error("Unexpected config option: '${option.optionName}'")
    }
  }

  internal companion object {
    const val OPTION_IS_RELEASE_BUILD = "isReleaseBuild"
    const val OPTION_ENABLE_DEVELOPMENT_APP_COMPONENT = "enableDevelopmentAppComponent"
  }
}
