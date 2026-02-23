package com.squareup.development.shell

import com.squareup.di.GraphFactoryProvider

abstract class DevelopmentApplication :
  GraphFactoryProvider<DevelopmentAppComponent.Factory> {

  /**
   * Uses reflection to find the Metro-generated `MetroComponent.Factory.Impl` singleton inside the
   * annotated class and returns it as a [DevelopmentAppComponent.Factory].
   */
  override fun provideGraphFactory(): DevelopmentAppComponent.Factory {
    val metroComponent =
      this::class.java.declaredClasses.first { it.simpleName == "MetroComponent" }
    val factory = metroComponent.declaredClasses.first { it.simpleName == "Factory" }
    val impl = factory.declaredClasses.first { it.simpleName == "Impl" }
    @Suppress("UNCHECKED_CAST")
    return impl.getField("INSTANCE").get(null) as DevelopmentAppComponent.Factory
  }
}
