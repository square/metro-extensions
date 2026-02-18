// MODULE: lib
package com.test

import com.squareup.api.RetrofitAuthenticated
import com.squareup.services.anvil.ContributesService

@ContributesService(Unit::class)
@RetrofitAuthenticated
interface MyService

// MODULE: main(lib)
package com.test

import com.squareup.api.RetrofitAuthenticated
import com.squareup.api.ServiceCreator
import com.squareup.development.FakeMode

@DependencyGraph(Unit::class)
abstract class MyGraph {
  abstract val myService: MyService

  @Provides @RetrofitAuthenticated
  fun provideServiceCreator(): ServiceCreator = ServiceCreator.NoOp

  @Provides @FakeMode
  fun provideFakeMode(): Boolean = false
}

fun box(): String {
  val graph = createGraph<MyGraph>()
  assertNotNull(graph.myService)
  return "OK"
}
