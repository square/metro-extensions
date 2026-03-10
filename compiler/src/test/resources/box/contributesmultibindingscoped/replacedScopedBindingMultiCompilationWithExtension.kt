// Reproduces a production issue where replacing a @ContributesMultibindingScoped class
// from a library module doesn't properly exclude its scoped binding in the app module.
// In the production case (android-register), Service1's dependencies are not available
// in the demo module, so if the scoped binding isn't excluded, Metro reports MissingBinding.

// MODULE: lib

package com.test

import com.squareup.dagger.ContributesMultibindingScoped
import com.squareup.dagger.ForScope
import mortar.Scoped

@GraphExtension(String::class)
interface ExtendedGraph {

  @ForScope(String::class) val scoped: Set<Scoped>
  val myService: MyService

  @ContributesTo(Unit::class)
  @GraphExtension.Factory
  interface Factory {
    fun createExtendedGraph(): ExtendedGraph
  }
}

interface MyService

@Inject
@ContributesBinding(String::class, binding = binding<MyService>())
@ContributesMultibindingScoped(String::class)
class Service1 : Scoped, MyService

// Service3 contributes a scoped binding that is NOT replaced.
@Inject
@ContributesMultibindingScoped(String::class)
class Service3 : Scoped

// MODULE: main(lib)
import com.squareup.dagger.ForScope
import com.test.ExtendedGraph
import com.test.MyService
import com.test.Service1
import com.test.Service3
import mortar.Scoped

// Service2 replaces Service1 and lives in a different module.
@Inject
@ContributesBinding(String::class, binding = binding<MyService>(), replaces = [Service1::class])
class Service2 : MyService

@DependencyGraph(Unit::class)
interface MyGraph

fun box(): String {
  val rootGraph = createGraph<MyGraph>()
  val graph = rootGraph.asContribution<ExtendedGraph.Factory>().createExtendedGraph()

  // Service2 replaces Service1, so Service1's scoped binding should also be excluded.
  // Only Service3's scoped binding should remain.
  assertEquals(1, graph.scoped.size, "Expected 1 scoped binding but got: ${graph.scoped}")
  assertTrue(
    graph.scoped.single() is Service3,
    "Expected Service3 but got: ${graph.scoped.single()}",
  )
  // Service2 should be the bound MyService implementation (replacing Service1).
  assertTrue(graph.myService is Service2, "Expected Service2 but got: ${graph.myService}")
  return "OK"
}
