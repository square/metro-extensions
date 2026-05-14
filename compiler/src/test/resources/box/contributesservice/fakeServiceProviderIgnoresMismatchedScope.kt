package com.test

import com.squareup.api.RetrofitAuthenticated
import com.squareup.api.ServiceCreator
import com.squareup.development.FakeMode
import com.squareup.services.anvil.ContributesService
import dev.zacsweers.metro.SingleIn

class AppScope

@ContributesService(Unit::class)
@RetrofitAuthenticated
interface MyService

@SingleIn(AppScope::class)
@ContributesService(Unit::class, replaces = [MyService::class])
class FakeMyService : MyService

@DependencyGraph(Unit::class)
@SingleIn(Unit::class)
interface MyGraph {
  val myService: MyService

  val fakeService: FakeMyService

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
  assertNull(providerMethod.getAnnotation(SingleIn::class.java))

  val graph = createGraphFactory<MyGraph.Factory>().create(fake = true)
  assertTrue(graph.myService is FakeMyService)
  assertTrue(graph.fakeService is FakeMyService)

  return "OK"
}
