package com.squareup.development.shell

import android.app.Application
import kotlin.reflect.KClass

annotation class DevelopmentAppComponent(
  val generateLoggedInComponent: Boolean = true,
  val featureScope: KClass<*> = Unit::class,
  val featureComponent: KClass<*> = Unit::class,
) {
  interface Factory {
    fun create(application: Application): Any
  }
}
