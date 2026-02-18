package com.test

import com.squareup.api.RealService
import com.squareup.api.RetrofitAuthenticated
import com.squareup.api.ServiceCreator
import com.squareup.dagger.SingleIn
import com.squareup.development.FakeMode
import com.squareup.services.anvil.ContributesService

@ContributesService(Unit::class)
@RetrofitAuthenticated
interface ServiceA

@ContributesService(Unit::class)
@RetrofitAuthenticated
interface ServiceB

@ContributesService(Unit::class, replaces = [ServiceA::class, ServiceB::class])
@Inject
class FakeService : ServiceA, ServiceB

@DependencyGraph(Unit::class)
@SingleIn(Unit::class)
interface MyGraph {
  val serviceA: ServiceA
  val serviceB: ServiceB

  @RealService
  val realServiceA: ServiceA

  @RealService
  val realServiceB: ServiceB

  val fakeService: FakeService

  @Provides @RetrofitAuthenticated
  fun provideServiceCreator(): ServiceCreator = ServiceCreator.NoOp

  @DependencyGraph.Factory
  interface Factory {
    fun create(@Provides @FakeMode fake: Boolean): MyGraph
  }
}

fun box(): String {
  var graph = createGraphFactory<MyGraph.Factory>().create(fake = true)
  assertTrue(graph.serviceA is FakeService, "Expected FakeServiceA")
  assertTrue(graph.serviceB is FakeService, "Expected FakeServiceB")
  assertTrue(graph.fakeService is FakeService, "Expected FakeServiceB")
  assertTrue(graph.realServiceA !is FakeService)
  assertTrue(graph.realServiceB !is FakeService)

  graph = createGraphFactory<MyGraph.Factory>().create(fake = false)
  assertTrue(graph.serviceA !is FakeService, "Expected real ServiceA")
  assertTrue(graph.serviceB !is FakeService, "Expected real ServiceB")

  return "OK"
}
