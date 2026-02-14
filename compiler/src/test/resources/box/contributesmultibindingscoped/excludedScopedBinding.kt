package com.test

import com.squareup.dagger.ContributesMultibindingScoped
import com.squareup.dagger.ForScope
import mortar.Scoped

@Inject
@ContributesMultibindingScoped(Unit::class)
class Service1 : Scoped

// Service2 contributes a scoped binding that is NOT excluded
@Inject
@ContributesMultibindingScoped(Unit::class)
class Service2 : Scoped

@DependencyGraph(Unit::class, excludes = [Service1::class])
interface MyGraph {
  @ForScope(Unit::class) val scoped: Set<Scoped>
}

fun box(): String {
  val graph = createGraph<MyGraph>()
  // Service1 is excluded from the graph, so only Service2's scoped binding should remain.
  assertEquals(1, graph.scoped.size, "Expected 1 scoped binding but got: ${graph.scoped}")
  assertTrue(
    graph.scoped.single() is Service2,
    "Expected Service2 but got: ${graph.scoped.single()}",
  )
  return "OK"
}
