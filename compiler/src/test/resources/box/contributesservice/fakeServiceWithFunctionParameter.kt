package com.test

import com.squareup.api.RetrofitAuthenticated
import com.squareup.api.ServiceCreator
import com.squareup.development.FakeMode
import com.squareup.services.anvil.ContributesService
import dev.zacsweers.metro.SingleIn

@ContributesService(Unit::class)
@RetrofitAuthenticated
interface MyService {
  fun value(): String
}

@ContributesService(Unit::class, replaces = [MyService::class])
class FakeMyService(private val valueProvider: () -> String) : MyService {
  override fun value(): String = valueProvider()
}

@DependencyGraph(Unit::class)
@SingleIn(Unit::class)
interface MyGraph {
  val myService: MyService

  @Provides fun provideValue(): String = "OK"

  @Provides @RetrofitAuthenticated fun provideServiceCreator(): ServiceCreator = ServiceCreator.NoOp

  @DependencyGraph.Factory
  interface Factory {
    fun create(@Provides @FakeMode fake: Boolean): MyGraph
  }
}

fun box(): String {
  val graph = createGraphFactory<MyGraph.Factory>().create(fake = true)
  assertEquals("OK", graph.myService.value())
  return "OK"
}
