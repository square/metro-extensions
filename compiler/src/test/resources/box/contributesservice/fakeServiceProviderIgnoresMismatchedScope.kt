package com.test

import com.squareup.api.RealService
import com.squareup.api.RetrofitAuthenticated
import com.squareup.api.ServiceCreator
import com.squareup.development.FakeMode
import com.squareup.services.anvil.ContributesService
import dev.zacsweers.metro.SingleIn

class AppScope
class LoggedInScope

class Dependency(val value: String)

@ContributesService(LoggedInScope::class)
@RetrofitAuthenticated
interface MyService {
  fun value(): String
}

@SingleIn(AppScope::class)
@ContributesService(LoggedInScope::class, replaces = [MyService::class])
class FakeMyService(private val dependency: Dependency) : MyService {
  var nextValue = dependency.value

  override fun value(): String = nextValue
}

@GraphExtension(LoggedInScope::class)
interface LoggedInGraph {
  val service: MyService

  @RealService val realService: MyService

  @ContributesTo(AppScope::class)
  @GraphExtension.Factory
  interface Factory {
    fun createLoggedInGraph(): LoggedInGraph
  }
}

@DependencyGraph(AppScope::class)
@SingleIn(AppScope::class)
interface AppGraph {
  val fakeService: FakeMyService

  @Provides fun provideDependency(): Dependency = Dependency("initial")

  @Provides @RetrofitAuthenticated fun provideServiceCreator(): ServiceCreator = ServiceCreator.NoOp

  @DependencyGraph.Factory
  interface Factory {
    fun create(@Provides @FakeMode fake: Boolean): AppGraph
  }
}

fun box(): String {
  val contributionClass =
    FakeMyService::class.java.declaredClasses.first { it.simpleName == "ServiceContribution" }
  val providerMethod =
    contributionClass.declaredMethods.firstOrNull {
      it.name == "provideContributedServiceReplacement"
    }

  assertNull(providerMethod)
  val providerContainer =
    FakeMyService::class.java.declaredClasses.first {
      it.simpleName == "FakeServiceProviderContribution"
    }
  val scopedProvider =
    providerContainer.declaredMethods.firstOrNull {
      it.name == "provideContributedServiceReplacement"
    }
  assertNotNull(scopedProvider)
  assertNotNull(scopedProvider.getAnnotation(SingleIn::class.java))

  val graph = createGraphFactory<AppGraph.Factory>().create(fake = true)
  graph.fakeService.nextValue = "updated"

  val loggedInGraph = graph.asContribution<LoggedInGraph.Factory>().createLoggedInGraph()
  assertTrue(loggedInGraph.service === graph.fakeService)
  assertEquals("updated", loggedInGraph.service.value())
  assertTrue(loggedInGraph.realService !is FakeMyService)

  return "OK"
}
