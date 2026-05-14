package com.test

import com.squareup.api.RealService
import com.squareup.api.RetrofitAuthenticated
import com.squareup.api.ServiceCreator
import com.squareup.development.FakeMode
import com.squareup.services.anvil.ContributesService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn

class LoggedInScope

class Dependency(val value: String)

@ContributesService(LoggedInScope::class)
@RetrofitAuthenticated
interface MyService {
  fun value(): String
}

@ContributesService(LoggedInScope::class, replaces = [MyService::class])
class FakeMyService(private val dependency: Dependency) : MyService {
  override fun value(): String = dependency.value
}

@GraphExtension(LoggedInScope::class)
interface LoggedInGraph {
  val myService: MyService

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

  @Provides fun provideDependency(): Dependency = Dependency("OK")

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
  assertNotNull(providerMethod)

  val parentProviderContainer =
    FakeMyService::class.java.declaredClasses.first {
      it.simpleName == "FakeServiceProviderContribution"
    }
  val parentProvider =
    parentProviderContainer.declaredMethods.firstOrNull {
      it.name == "provideContributedServiceReplacement"
    }
  assertNotNull(parentProvider)
  assertNull(parentProvider.getAnnotation(SingleIn::class.java))

  val graph = createGraphFactory<AppGraph.Factory>().create(fake = true)
  assertEquals("OK", graph.fakeService.value())

  val loggedInGraph = graph.asContribution<LoggedInGraph.Factory>().createLoggedInGraph()
  assertEquals("OK", loggedInGraph.myService.value())
  assertTrue(loggedInGraph.realService !is FakeMyService)

  return "OK"
}
