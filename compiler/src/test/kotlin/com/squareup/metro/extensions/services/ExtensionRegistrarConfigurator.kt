package com.squareup.metro.extensions.services

import com.squareup.metro.extensions.SquareMetroExtensionsConfig
import com.squareup.metro.extensions.SquareMetroExtensionsPluginComponentRegistrar
import dev.zacsweers.metro.compiler.MetroCommandLineProcessor
import dev.zacsweers.metro.compiler.MetroCompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

fun TestConfigurationBuilder.configurePlugin() {
  useConfigurators(::ExtensionRegistrarConfigurator)
  useDirectives(MetroDirectives)
  configureAnnotations()
  configureMetroRuntime()
}

fun TestConfigurationBuilder.configureMetroImports() {
  useSourcePreprocessor(::MetroImportsPreprocessor)
}

fun TestConfigurationBuilder.configureKotlinTestImports() {
  useSourcePreprocessor(::KotlinTestImportsPreprocessor)
}

private class ExtensionRegistrarConfigurator(testServices: TestServices) :
  EnvironmentConfigurator(testServices) {
  private val metroCliProcessor = MetroCommandLineProcessor()
  private val metroRegistrar = MetroCompilerPluginRegistrar()
  private val extensionsRegistrar = SquareMetroExtensionsPluginComponentRegistrar()

  override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
    module: TestModule,
    configuration: CompilerConfiguration,
  ) {
    // Configure Metro options from directives before registering
    if (MetroDirectives.GENERATE_CONTRIBUTION_HINTS_IN_FIR in module.directives) {
      val option =
        metroCliProcessor.pluginOptions.first {
          it.optionName == "generate-contribution-hints-in-fir"
        }
      metroCliProcessor.processOption(option, "true", configuration)
    }

    // Configure isReleaseBuild from directive (defaults to false)
    if (MetroDirectives.IS_RELEASE_BUILD in module.directives) {
      configuration.put(SquareMetroExtensionsConfig.IS_RELEASE_BUILD_KEY, true)
    }

    // Configure enableDevelopmentAppComponent from directive (defaults to true)
    if (MetroDirectives.DISABLE_DEVELOPMENT_APP_COMPONENT in module.directives) {
      configuration.put(SquareMetroExtensionsConfig.ENABLE_DEVELOPMENT_APP_COMPONENT_KEY, false)
    }

    // Register Metro's actual compiler plugin
    with(metroRegistrar) { registerExtensions(configuration) }
    // Register our custom extensions
    with(extensionsRegistrar) { registerExtensions(configuration) }
  }
}
