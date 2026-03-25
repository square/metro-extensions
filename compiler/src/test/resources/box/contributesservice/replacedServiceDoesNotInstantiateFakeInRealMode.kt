package com.test

import com.squareup.api.RetrofitAuthenticated
import com.squareup.api.ServiceCreator
import com.squareup.dagger.SingleIn
import com.squareup.development.FakeMode
import com.squareup.services.anvil.ContributesService

@ContributesService(Unit::class)
@RetrofitAuthenticated
interface MyService

@SingleIn(Unit::class)
@ContributesService(Unit::class, replaces = [MyService::class])
@Inject
class FakeMyService : MyService {
  init {
    error("Fake service should not be instantiated in real mode")
  }
}

@DependencyGraph(Unit::class)
@SingleIn(Unit::class)
interface MyGraph {
  val myService: MyService

  @Provides @RetrofitAuthenticated
  fun provideServiceCreator(): ServiceCreator = ServiceCreator.NoOp

  @DependencyGraph.Factory
  interface Factory {
    fun create(@Provides @FakeMode fake: Boolean): MyGraph
  }
}

fun box(): String {
  val realGraph = createGraphFactory<MyGraph.Factory>().create(fake = false)
  assertTrue(realGraph.myService !is FakeMyService, "Expected real service in real mode")

  val fakeGraph = createGraphFactory<MyGraph.Factory>().create(fake = true)
  val error = assertFailsWith<IllegalStateException> { fakeGraph.myService }
  assertTrue(
    error.message?.contains("Fake service should not be instantiated in real mode") == true,
    "Expected fake constructor failure but was: ${error.message}",
  )

  return "OK"
}
