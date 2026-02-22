package com.test

import android.app.Application
import com.squareup.development.shell.DevelopmentAppComponent
import com.squareup.development.shell.DevelopmentApplication

@DevelopmentAppComponent
class MyApp : DevelopmentApplication()

fun box(): String {
  val factory = createGraphFactory<MyApp.MetroComponent.Factory>()
  val component = factory.create(Application())
  assertTrue(component is MyApp.MetroComponent)
  return "OK"
}
