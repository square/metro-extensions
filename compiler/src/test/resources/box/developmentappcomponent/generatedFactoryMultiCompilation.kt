// MODULE: lib
package com.test

import com.squareup.development.shell.DevelopmentAppComponent
import com.squareup.development.shell.DevelopmentApplication

@DevelopmentAppComponent
class MyApp : DevelopmentApplication()

// MODULE: main(lib)
package com.test

import android.app.Application

fun box(): String {
  val factory = createGraphFactory<MyApp.MetroComponent.Factory>()
  // The generated override's covariant return type must survive serialization, rather than
  // resolving to the inherited DevelopmentAppComponent.Factory.create(...): Any declaration.
  val component: MyApp.MetroComponent = factory.create(Application())
  assertTrue(component is MyApp.MetroComponent)
  return "OK"
}
