// Real and fake service compiled together in the same library module, graph in a separate
// consumer module. This is the android-register pattern where :common:api defines the real
// service and :common:api-fake defines the fake, both compiled in the same Gradle module,
// while the graph lives in a downstream :demo module.

// MODULE: lib
package com.test

import com.squareup.api.RetrofitAuthenticated
import com.squareup.services.anvil.ContributesService

@ContributesService(Unit::class)
@RetrofitAuthenticated
interface MyService

@ContributesService(Unit::class, replaces = [MyService::class])
@Inject
class FakeMyService : MyService

// MODULE: main(lib)
package com.test

import com.squareup.api.RealService
import com.squareup.api.RetrofitAuthenticated
import com.squareup.api.ServiceCreator
import com.squareup.dagger.SingleIn
import com.squareup.development.FakeMode

@DependencyGraph(Unit::class)
@SingleIn(Unit::class)
interface MyGraph {
  val myService: MyService

  @RealService
  val realService: MyService

  val fakeService: FakeMyService

  @Provides @RetrofitAuthenticated
  fun provideServiceCreator(): ServiceCreator = ServiceCreator.NoOp

  @DependencyGraph.Factory
  interface Factory {
    fun create(@Provides @FakeMode fake: Boolean): MyGraph
  }
}

fun box(): String {
  var graph = createGraphFactory<MyGraph.Factory>().create(fake = true)
  assertTrue(graph.myService is FakeMyService, "Expected FakeMyService in fake mode")
  assertTrue(graph.fakeService is FakeMyService)
  assertTrue(graph.realService !is FakeMyService)

  graph = createGraphFactory<MyGraph.Factory>().create(fake = false)
  assertTrue(graph.myService !is FakeMyService, "Expected real MyService in real mode")
  assertTrue(graph.fakeService is FakeMyService)
  assertTrue(graph.realService !is FakeMyService)

  return "OK"
}
