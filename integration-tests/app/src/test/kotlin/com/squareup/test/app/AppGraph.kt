package com.squareup.test.app

import com.squareup.api.RealService
import com.squareup.api.RetrofitAuthenticated
import com.squareup.api.ServiceCreator
import com.squareup.dagger.AppScope
import com.squareup.dagger.ForScope
import com.squareup.dagger.SingleIn
import com.squareup.development.FakeMode
import com.squareup.featureflags.FeatureFlag
import com.squareup.test.app.data.FakeLibService
import com.squareup.test.lib.LibService
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import mortar.Scoped

@DependencyGraph(AppScope::class)
@SingleIn(AppScope::class)
interface AppGraph {
  @ForScope(AppScope::class) val scoped: Set<Scoped>

  val featureFlags: Set<FeatureFlag>

  val appService: AppService

  val libService: LibService

  @RealService val realLibService: LibService
  val fakeLibService: FakeLibService

  @Provides @RetrofitAuthenticated fun provideServiceCreator(): ServiceCreator = ServiceCreator.NoOp

  @DependencyGraph.Factory
  interface Factory {
    fun create(@Provides @FakeMode fakeMode: Boolean): AppGraph
  }
}
