package com.squareup.test.app

import com.squareup.dagger.ForScope
import dev.zacsweers.metro.DependencyGraph
import mortar.Scoped

@DependencyGraph(Unit::class)
interface AppGraph {
  @ForScope(Unit::class) val scoped: Set<Scoped>
}
