// MODULE: lib
package com.test

import com.squareup.development.shell.DevelopmentApplication

abstract class BaseDevelopmentApp : DevelopmentApplication()

// MODULE: main(lib)
package com.test

import android.app.Application
import com.squareup.development.shell.DevelopmentAppComponent

@DevelopmentAppComponent
class MyApp : BaseDevelopmentApp()

fun box(): String {
  val factory = createGraphFactory<MyApp.MetroComponent.Factory>()
  val component = factory.create(Application())
  assertTrue(component is MyApp.MetroComponent)
  return "OK"
}
