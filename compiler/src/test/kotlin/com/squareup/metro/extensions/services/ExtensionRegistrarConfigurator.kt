package com.squareup.metro.extensions.services

import com.squareup.metro.extensions.SquareMetroExtensionsPluginComponentRegistrar
import dev.zacsweers.metro.compiler.MetroCompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

fun TestConfigurationBuilder.configurePlugin() {
  useConfigurators(::ExtensionRegistrarConfigurator)
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
  private val metroRegistrar = MetroCompilerPluginRegistrar()
  private val extensionsRegistrar = SquareMetroExtensionsPluginComponentRegistrar()

  override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
    module: TestModule,
    configuration: CompilerConfiguration,
  ) {
    // Register Metro's actual compiler plugin
    with(metroRegistrar) { registerExtensions(configuration) }
    // Register our custom extensions
    with(extensionsRegistrar) { registerExtensions(configuration) }
  }
}
