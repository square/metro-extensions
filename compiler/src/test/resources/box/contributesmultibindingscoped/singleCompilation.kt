package com.test

import com.squareup.dagger.ContributesMultibindingScoped
import com.squareup.dagger.ForScope
import mortar.Scoped

@Inject
@ContributesMultibindingScoped(Unit::class)
class MyTestClass : Scoped

@DependencyGraph(Unit::class)
interface MyGraph {
  @ForScope(Unit::class) val scoped: Set<Scoped>
}

fun box(): String {
  val graph = createGraph<MyGraph>()
  assertNotNull(graph.scoped.singleOrNull())
  return "OK"
}
