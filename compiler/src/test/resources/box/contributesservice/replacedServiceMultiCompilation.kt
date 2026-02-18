// MODULE: lib
package com.test

import com.squareup.api.RetrofitAuthenticated
import com.squareup.services.anvil.ContributesService

@ContributesService(Unit::class)
@RetrofitAuthenticated
interface MyService

// MODULE: fake(lib)
package com.test

import com.squareup.services.anvil.ContributesService

@ContributesService(Unit::class, replaces = [MyService::class])
@Inject
class FakeMyService : MyService

// MODULE: main(lib, fake)
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
  fun provideFakeMode(): Boolean = true
}

fun box(): String {
  val graph = createGraph<MyGraph>()
  val service = graph.myService
  assertNotNull(service)
  assertTrue(service is FakeMyService, "Expected FakeMyService but got: ${service::class}")
  return "OK"
}
