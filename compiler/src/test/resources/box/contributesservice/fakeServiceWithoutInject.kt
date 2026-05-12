package com.test

import com.squareup.api.RealService
import com.squareup.api.RetrofitAuthenticated
import com.squareup.api.ServiceCreator
import com.squareup.development.FakeMode
import com.squareup.services.anvil.ContributesService
import dev.zacsweers.metro.SingleIn

class Dependency(val value: String)

@ContributesService(Unit::class)
@RetrofitAuthenticated
interface MyService {
  fun value(): String
}

@ContributesService(Unit::class, replaces = [MyService::class])
class FakeMyService(private val dependency: Dependency) : MyService {
  override fun value(): String = dependency.value
}

@DependencyGraph(Unit::class)
@SingleIn(Unit::class)
interface MyGraph {
  val myService: MyService

  @RealService val realService: MyService

  val fakeService: FakeMyService

  @Provides fun provideDependency(): Dependency = Dependency("OK")

  @Provides @RetrofitAuthenticated fun provideServiceCreator(): ServiceCreator = ServiceCreator.NoOp

  @DependencyGraph.Factory
  interface Factory {
    fun create(@Provides @FakeMode fake: Boolean): MyGraph
  }
}

fun box(): String {
  val contributionClass =
    FakeMyService::class.java.declaredClasses.first { it.simpleName == "ServiceContribution" }
  val providerMethod =
    contributionClass.declaredMethods.firstOrNull {
      it.name == "provideContributedServiceReplacement"
    }
  assertNotNull(providerMethod)

  var graph = createGraphFactory<MyGraph.Factory>().create(fake = true)
  assertEquals("OK", graph.myService.value())
  assertEquals("OK", graph.fakeService.value())

  graph = createGraphFactory<MyGraph.Factory>().create(fake = false)
  assertTrue(graph.realService !is FakeMyService)

  return "OK"
}
