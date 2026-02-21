package com.test

import com.squareup.api.RetrofitAuthenticated
import com.squareup.api.ServiceCreator
import com.squareup.development.FakeMode
import com.squareup.services.anvil.ContributesService

@ContributesService(Unit::class)
@RetrofitAuthenticated
interface MyService

@ContributesService(Unit::class, replaces = [MyService::class])
@Inject
class FakeMyService : MyService

@DependencyGraph(Unit::class)
interface MyGraph {
  val myService: MyService

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
  assertTrue(graph.myService is FakeMyService, "Expected FakeMyService")
  assertTrue(graph.fakeService is FakeMyService)

  graph = createGraphFactory<MyGraph.Factory>().create(fake = false)
  assertTrue(graph.myService !is FakeMyService, "Expected real MyService")
  assertTrue(graph.fakeService is FakeMyService)

  return "OK"
}
