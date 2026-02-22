// Verifies that generateLoggedInComponent = false adds excludes to @DependencyGraph,
// causing the excluded module's bindings to be removed from the graph.
//
// LoginScreenModule contributes a String binding to AppScope. Without excludes, the graph
// would contain this binding and there would be a duplicate with AppStringProvider.
// With excludes, LoginScreenModule is excluded, so only AppStringProvider's binding remains.

// GENERATE_CONTRIBUTION_HINTS_IN_FIR

// MODULE: deps
package com.squareup.development.shell.login.screen

import com.squareup.dagger.AppScope

@ContributesTo(AppScope::class)
interface LoginScreenModule {
  @Provides fun provideLoginString(): String = "from LoginScreenModule"
}

// MODULE: deps2
package com.squareup.development.shell.component

class DevelopmentLoggedInComponent

// MODULE: main(deps, deps2)
package com.test

import android.app.Application
import com.squareup.dagger.AppScope
import com.squareup.development.shell.DevelopmentAppComponent
import com.squareup.development.shell.DevelopmentApplication

@ContributesTo(AppScope::class)
interface AppStringProvider {
  @Provides fun provideAppString(): String = "from AppStringProvider"
}

@DevelopmentAppComponent(generateLoggedInComponent = false)
class MyApp : DevelopmentApplication()

fun box(): String {
  val factory = createGraphFactory<MyApp.MetroComponent.Factory>()
  val component = factory.create(Application())
  assertTrue(component is MyApp.MetroComponent)
  return "OK"
}
