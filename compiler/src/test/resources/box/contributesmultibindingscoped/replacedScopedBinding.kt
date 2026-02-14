package com.test

import com.squareup.dagger.ContributesMultibindingScoped
import com.squareup.dagger.ForScope
import mortar.Scoped

interface MyService

@Inject
@ContributesBinding(Unit::class, binding = binding<MyService>())
@ContributesMultibindingScoped(Unit::class)
class Service1 : Scoped, MyService

@Inject
@ContributesBinding(Unit::class, binding = binding<MyService>(), replaces = [Service1::class])
class Service2 : MyService

// Service3 contributes a scoped binding that is NOT replaced
@Inject
@ContributesMultibindingScoped(Unit::class)
class Service3 : Scoped

@DependencyGraph(Unit::class)
interface MyGraph {
  val myService: MyService
  @ForScope(Unit::class) val scoped: Set<Scoped>
}

fun box(): String {
  val graph = createGraph<MyGraph>()
  // Service2 replaces Service1, so Service1's scoped binding should also be excluded.
  // Only Service3's scoped binding should remain.
  assertEquals(1, graph.scoped.size, "Expected 1 scoped binding but got: ${graph.scoped}")
  assertTrue(
    graph.scoped.single() is Service3,
    "Expected Service3 but got: ${graph.scoped.single()}",
  )
  // Service2 should be the bound MyService implementation (replacing Service1)
  assertTrue(graph.myService is Service2, "Expected Service2 but got: ${graph.myService}")
  return "OK"
}
