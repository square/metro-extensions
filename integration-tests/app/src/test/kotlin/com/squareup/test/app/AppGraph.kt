package com.squareup.test.app

import com.squareup.api.RetrofitAuthenticated
import com.squareup.api.ServiceCreator
import com.squareup.dagger.ForScope
import com.squareup.dagger.SingleIn
import com.squareup.development.FakeMode
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import mortar.Scoped

@DependencyGraph(Unit::class)
@SingleIn(Unit::class)
interface AppGraph {
  @ForScope(Unit::class) val scoped: Set<Scoped>

  /** Not replaced — always the real service. */
  val appService: AppService

  @Provides @RetrofitAuthenticated
  fun provideServiceCreator(): ServiceCreator = ServiceCreator.NoOp

  @DependencyGraph.Factory
  interface Factory {
    fun create(@Provides @FakeMode fakeMode: Boolean): AppGraph
  }
}
