package com.test

import com.squareup.api.RetrofitAuthenticated
import com.squareup.api.ServiceCreator
import com.squareup.dagger.SingleIn
import com.squareup.development.FakeMode
import com.squareup.services.anvil.ContributesService

@ContributesService(Unit::class)
@RetrofitAuthenticated
interface MyService

@DependencyGraph(Unit::class)
@SingleIn(Unit::class)
interface MyGraph {
  val myService: MyService

  @Provides @RetrofitAuthenticated
  fun provideServiceCreator(): ServiceCreator = ServiceCreator.NoOp

  @Provides @FakeMode
  fun provideFakeMode(): Boolean = false
}

fun box(): String {
  val graph = createGraph<MyGraph>()
  assertNotNull(graph.myService)
  return "OK"
}
